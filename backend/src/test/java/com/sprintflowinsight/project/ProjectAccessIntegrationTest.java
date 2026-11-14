package com.sprintflowinsight.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprintflowinsight.auth.User;
import com.sprintflowinsight.auth.UserIdentity;
import com.sprintflowinsight.auth.UserRepository;
import com.sprintflowinsight.model.Sprint;
import com.sprintflowinsight.repository.SprintRepository;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Authorisation rules and their query cost, exercised through the real HTTP layer. */
@SpringBootTest(
    properties = {
      "app.security.auth-rate-limit.enabled=false",
      "spring.datasource.url=${SPRINTFLOW_TEST_DB_URL:jdbc:h2:mem:access-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}",
      "spring.datasource.driver-class-name=${SPRINTFLOW_TEST_DB_DRIVER:org.h2.Driver}",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.jpa.properties.hibernate.generate_statistics=true",
      "spring.flyway.enabled=true"
    })
@AutoConfigureMockMvc
class ProjectAccessIntegrationTest {
  @Autowired private MockMvc mvc;
  @Autowired private UserRepository users;
  @Autowired private ProjectRepository projects;
  @Autowired private ProjectMemberRepository members;
  @Autowired private SprintRepository sprints;
  @Autowired private EntityManagerFactory entityManagerFactory;

  @Test
  void listingSprintsAcrossProjectsCostsTheSameNumberOfQueriesForAnyProjectCount() throws Exception {
    User reader = user();
    User stranger = user();
    for (int i = 0; i < 2; i++) sprintIn(project(reader, null));
    long withTwo = statementsFor(() -> mvc.perform(get("/api/sprints").with(as(reader))));

    for (int i = 0; i < 6; i++) sprintIn(project(stranger, reader));
    Project hidden = project(stranger, null);
    sprintIn(hidden);
    Project trashed = project(reader, null);
    trashed.setTrashedAt(Instant.now());
    projects.save(trashed);
    sprintIn(trashed);

    long withEight = statementsFor(() -> mvc.perform(get("/api/sprints").with(as(reader))));
    assertThat(withEight).isEqualTo(withTwo);

    mvc.perform(get("/api/sprints").with(as(reader)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(8))
        .andExpect(jsonPath("$[?(@.projectId == " + hidden.getId() + ")]").isEmpty())
        .andExpect(jsonPath("$[?(@.projectId == " + trashed.getId() + ")]").isEmpty());
  }

  @Test
  void repeatedChecksWithinOneRequestShareASingleAccessQuery() throws Exception {
    User owner = user();
    Project project = project(owner, null);
    long cost =
        statementsFor(
            () -> mvc.perform(get("/api/sprints").param("projectId", project.getId().toString()).with(as(owner))));
    // user lookup + one access row + the sprint list
    assertThat(cost).isLessThanOrEqualTo(3);
  }

  @Test
  void archivedProjectsRejectMemberChangesButCanBeUnarchived() throws Exception {
    User owner = user();
    User colleague = user();
    Project project = project(owner, null);
    project.setArchivedAt(Instant.now());
    projects.save(project);

    mvc.perform(
            post("/api/projects/" + project.getId() + "/members")
                .with(as(owner))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + colleague.getEmail() + "\",\"role\":\"Member\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));
    mvc.perform(
            delete("/api/projects/" + project.getId() + "/activity").with(as(owner)).with(csrf()))
        .andExpect(status().isConflict());

    mvc.perform(
            put("/api/projects/" + project.getId() + "/archive")
                .with(as(owner))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"archived\":false}"))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/projects/" + project.getId() + "/members")
                .with(as(owner))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + colleague.getEmail() + "\",\"role\":\"Member\"}"))
        .andExpect(status().is2xxSuccessful());
  }

  @Test
  void viewersReadButCannotWriteAndOutsidersAreForbidden() throws Exception {
    User owner = user();
    User viewer = user();
    User outsider = user();
    Project project = project(owner, null);
    member(project, viewer, "Viewer");

    mvc.perform(get("/api/projects/" + project.getId() + "/role").with(as(viewer)))
        .andExpect(status().isOk());
    mvc.perform(
            delete("/api/projects/" + project.getId() + "/activity").with(as(viewer)).with(csrf()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    mvc.perform(get("/api/projects/" + project.getId() + "/role").with(as(outsider)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/projects/999999/role").with(as(outsider)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  private interface Call {
    void run() throws Exception;
  }

  private long statementsFor(Call call) throws Exception {
    Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.clear();
    call.run();
    return statistics.getPrepareStatementCount();
  }

  private RequestPostProcessor as(User user) {
    return authentication(
        new UsernamePasswordAuthenticationToken(
            new UserIdentity(user.getId(), user.getEmail()),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_USER"))));
  }

  private User user() {
    User user = new User();
    user.setEmail("access-" + UUID.randomUUID() + "@example.test");
    user.setPassword("not-used-in-this-test");
    return users.save(user);
  }

  private Project project(User owner, User member) {
    Project project =
        projects.save(new Project("Access " + UUID.randomUUID(), "Access rules", owner.getEmail()));
    if (member != null) member(project, member, "Member");
    return project;
  }

  private void member(Project project, User user, String role) {
    ProjectMember member = new ProjectMember();
    member.setProjectId(project.getId());
    member.setEmail(user.getEmail());
    member.setRole(role);
    members.save(member);
  }

  private void sprintIn(Project project) {
    Sprint sprint = new Sprint("Sprint " + UUID.randomUUID(), "Goal", "Planned", 0);
    sprint.setProjectId(project.getId());
    sprints.save(sprint);
  }
}
