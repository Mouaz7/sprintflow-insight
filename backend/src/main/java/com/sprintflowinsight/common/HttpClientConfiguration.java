package com.sprintflowinsight.common;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** One bounded outbound client, including the Google signing-key requests. */
@Configuration(proxyBeanMethods = false)
public class HttpClientConfiguration {
  @Bean
  public RestClient outboundRestClient(
      RestClient.Builder builder,
      @Value("${app.http.connect-timeout:3s}") Duration connectTimeout,
      @Value("${app.http.read-timeout:5s}") Duration readTimeout) {
    var http =
        HttpClient.newBuilder()
            .connectTimeout(connectTimeout)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    var factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(readTimeout);
    return builder.requestFactory(factory).build();
  }
}
