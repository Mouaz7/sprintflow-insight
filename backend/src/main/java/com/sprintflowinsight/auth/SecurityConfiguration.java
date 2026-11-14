package com.sprintflowinsight.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprintflowinsight.common.error.ErrorCode;
import com.sprintflowinsight.common.error.Problems;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import java.util.List;
import java.util.Locale;

@Configuration
public class SecurityConfiguration {
    private static final org.slf4j.Logger SECURITY_LOG = org.slf4j.LoggerFactory.getLogger("security.events");
    @org.springframework.beans.factory.annotation.Value("${app.password-reset.base-url:http://localhost:8081}") private String publicUrl;
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean UserDetailsService userDetailsService(UserRepository users) {
        return email -> users.findByEmail(email.trim().toLowerCase(Locale.ROOT))
            .map(user -> org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
                .password(user.getPassword()).roles("USER").build())
            .orElseThrow(() -> new UsernameNotFoundException("Incorrect email or password."));
    }
    @Bean AuthenticationManager authenticationManager(UserDetailsService details, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(details); provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
    @Bean SecurityContextRepository securityContextRepository() { return new HttpSessionSecurityContextRepository(); }
    @Bean HttpSessionCsrfTokenRepository csrfTokenRepository() { return new HttpSessionCsrfTokenRepository(); }
    @Bean SessionAuthenticationStrategy sessionAuthenticationStrategy(HttpSessionCsrfTokenRepository csrf) {
        return new CompositeSessionAuthenticationStrategy(List.of(new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrf)));
    }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contexts,
            HttpSessionCsrfTokenRepository csrf, ObjectMapper json) throws Exception {
        http.securityContext(context -> context.securityContextRepository(contexts).requireExplicitSave(true))
            .csrf(config -> config.csrfTokenRepository(csrf))
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; "
                    + "script-src 'self' https://accounts.google.com; "
                    + "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com https://accounts.google.com/gsi/style; font-src 'self' https://fonts.gstatic.com; img-src 'self' data: https:; "
                    + "connect-src 'self' https://accounts.google.com; frame-src https://accounts.google.com; "
                    + "object-src 'none'; base-uri 'self'; form-action 'self'"))
                .addHeaderWriter((request, response) -> {
                    String forwardedUri = (String) request.getAttribute(jakarta.servlet.RequestDispatcher.FORWARD_REQUEST_URI);
                    String originalUri = forwardedUri == null ? request.getRequestURI() : forwardedUri;
                    String originalQuery = forwardedUri == null ? request.getQueryString()
                        : (String) request.getAttribute(jakarta.servlet.RequestDispatcher.FORWARD_QUERY_STRING);
                    boolean localLogin = (publicUrl.startsWith("http://localhost:") || publicUrl.equals("http://localhost")
                        || publicUrl.startsWith("http://127.0.0.1:") || publicUrl.equals("http://127.0.0.1"))
                        && "/".equals(originalUri) && originalQuery == null;
                    response.setHeader("Referrer-Policy", localLogin
                        ? "no-referrer-when-downgrade" : "strict-origin-when-cross-origin");
                })
                .addHeaderWriter(new StaticHeadersWriter("Cross-Origin-Opener-Policy", "same-origin-allow-popups"))
                .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy", "camera=(), microphone=(), geolocation=()")))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/shared-reports/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/csrf", "/api/auth/google/config", "/api/auth/password/reset/available").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register", "/api/auth/google",
                    "/api/auth/password/reset/request", "/api/auth/password/reset/confirm", "/api/auth/password/reset/recovery").permitAll()
                .requestMatchers("/api/auth/password/reset/email-config").denyAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/google/config").denyAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, error) -> writeProblem(json, request, response, ErrorCode.UNAUTHENTICATED, "Sign in to continue."))
                .accessDeniedHandler((request, response, error) -> { SECURITY_LOG.warn("Denied protected request."); writeProblem(json, request, response, ErrorCode.FORBIDDEN, "Access denied. Refresh the page and try again."); }))
            .logout(logout -> logout.logoutUrl("/api/auth/logout").invalidateHttpSession(true).deleteCookies("JSESSIONID")
                .logoutSuccessHandler((request, response, auth) -> response.setStatus(204)))
            .formLogin(form -> form.disable()).httpBasic(basic -> basic.disable());
        return http.build();
    }

    /** Writes the same RFC 9457 problem shape as the MVC handler, for filter-level failures. */
    public static void writeProblem(ObjectMapper json, jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response, ErrorCode code, String detail) throws java.io.IOException {
        org.springframework.http.ProblemDetail problem = Problems.of(code, detail);
        problem.setInstance(java.net.URI.create(request.getRequestURI()));
        response.setStatus(code.status().value());
        response.setContentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getOutputStream(), Problems.asMap(problem));
    }
}
