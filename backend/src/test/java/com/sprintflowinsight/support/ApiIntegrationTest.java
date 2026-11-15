package com.sprintflowinsight.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.sprintflowinsight.auth.User;
import com.sprintflowinsight.auth.UserIdentity;
import com.sprintflowinsight.auth.UserRepository;
import com.sprintflowinsight.project.Project;
import com.sprintflowinsight.project.ProjectMember;
import com.sprintflowinsight.project.ProjectMemberRepository;
import com.sprintflowinsight.project.ProjectRepository;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Shared fixture for HTTP-level integration tests: MockMvc against the full application, data
 * created directly through repositories, and callers authenticated without a login round trip.
 * All subclasses share one Spring context.
 */
@org.springframework.context.annotation.Import(com.sprintflowinsight.support.PostgresTestContainer.class)
@SpringBootTest(
    properties = {
      "app.security.auth-rate-limit.enabled=false",
      "spring.jpa.properties.hibernate.generate_statistics=true"
    })
@AutoConfigureMockMvc
public abstract class ApiIntegrationTest {
  @Autowired protected MockMvc mvc;
  @Autowired protected UserRepository users;
  @Autowired protected ProjectRepository projects;
  @Autowired protected ProjectMemberRepository members;
  @Autowired private EntityManagerFactory entityManagerFactory;

  protected interface Call {
    void run() throws Exception;
  }

  /** Number of JDBC statements prepared while {@code call} runs. */
  protected long statementsFor(Call call) throws Exception {
    Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.clear();
    call.run();
    return statistics.getPrepareStatementCount();
  }

  protected RequestPostProcessor as(User user) {
    return authentication(
        new UsernamePasswordAuthenticationToken(
            new UserIdentity(user.getId(), user.getEmail()),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_USER"))));
  }

  protected User user() {
    User user = new User();
    user.setEmail("user-" + UUID.randomUUID() + "@example.test");
    user.setPassword("not-used-in-this-test");
    return users.save(user);
  }

  protected Project project(User owner, User member) {
    Project project =
        projects.save(new Project("Project " + UUID.randomUUID(), "Test project", owner.getEmail()));
    if (member != null) member(project, member, "Member");
    return project;
  }

  protected void member(Project project, User user, String role) {
    ProjectMember member = new ProjectMember();
    member.setProjectId(project.getId());
    member.setEmail(user.getEmail());
    member.setRole(role);
    members.save(member);
  }
}
