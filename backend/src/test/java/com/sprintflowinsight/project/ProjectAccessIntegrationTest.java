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
import com.sprintflowinsight.support.ApiIntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Authorisation rules and their query cost, exercised through the real HTTP layer. */
class ProjectAccessIntegrationTest extends ApiIntegrationTest {
  @Autowired private SprintRepository sprints;

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

  @Test
  void adminsManageMembersButOnlyTheOwnerChangesOtherAdmins() throws Exception {
    User owner = user();
    User admin = user();
    User otherAdmin = user();
    User colleague = user();
    Project project = project(owner, null);
    member(project, admin, "Admin");
    member(project, otherAdmin, "Admin");

    // An admin may add and remove ordinary members ...
    mvc.perform(memberPut(project, admin, colleague, "Member")).andExpect(status().isOk());
    // ... but may not appoint admins, nor change or remove another admin.
    mvc.perform(memberPut(project, admin, colleague, "Admin"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.detail").value("Only the project owner can appoint admins."));
    mvc.perform(memberPut(project, admin, otherAdmin, "Viewer"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.detail").value("Only the project owner can change or remove another admin."));
    mvc.perform(
            delete("/api/projects/" + project.getId() + "/members/" + otherAdmin.getEmail())
                .with(as(admin))
                .with(csrf()))
        .andExpect(status().isForbidden());
    assertThat(members.findByProjectIdAndEmailIgnoreCase(project.getId(), otherAdmin.getEmail()))
        .get()
        .extracting(ProjectMember::getRole)
        .isEqualTo("Admin");

    // An admin may step down, and the owner may change any admin.
    mvc.perform(memberPut(project, admin, admin, "Member")).andExpect(status().isOk());
    mvc.perform(memberPut(project, owner, otherAdmin, "Viewer")).andExpect(status().isOk());
  }

  @Test
  void movingAProjectToTheRecycleBinRequiresTheCurrentVersion() throws Exception {
    User owner = user();
    Project project = project(owner, null);
    String path = "/api/workspace/trash/project/" + project.getId();
    String body = "{\"restore\":false}";
    mvc.perform(put(path).with(as(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().is(428))
        .andExpect(jsonPath("$.code").value("PRECONDITION_REQUIRED"));
    mvc.perform(
            put(path)
                .with(as(owner))
                .with(csrf())
                .header("If-Match", "\"" + (project.getVersion() + 1) + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isPreconditionFailed());
    mvc.perform(
            put(path)
                .with(as(owner))
                .with(csrf())
                .header("If-Match", "\"" + project.getVersion() + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());
    assertThat(projects.findById(project.getId()).orElseThrow().getTrashedAt()).isNotNull();
  }

  @Test
  void theDatabaseIndexRejectsASecondActiveSprintWithAReadableConflict() {
    User owner = user();
    Project project = project(owner, null);
    Sprint first = new Sprint("Active one", "Goal", "Active", 0);
    first.setProjectId(project.getId());
    sprints.saveAndFlush(first);
    Sprint second = new Sprint("Active two", "Goal", "Active", 0);
    second.setProjectId(project.getId());
    var thrown =
        org.assertj.core.api.Assertions.catchThrowableOfType(
            org.springframework.dao.DataIntegrityViolationException.class,
            () -> sprints.saveAndFlush(second));
    var response =
        new com.sprintflowinsight.ApiExceptionHandler()
            .handleIntegrityViolation(
                thrown,
                new org.springframework.web.context.request.ServletWebRequest(
                    new org.springframework.mock.web.MockHttpServletRequest()));
    assertThat(response.getBody().getDetail())
        .isEqualTo("Only one sprint can be active in a project at a time.");
  }

  private org.springframework.test.web.servlet.RequestBuilder memberPut(
      Project project, User caller, User target, String role) {
    return post("/api/projects/" + project.getId() + "/members")
        .with(as(caller))
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + target.getEmail() + "\",\"role\":\"" + role + "\"}");
  }

  private void sprintIn(Project project) {
    Sprint sprint = new Sprint("Sprint " + UUID.randomUUID(), "Goal", "Planned", 0);
    sprint.setProjectId(project.getId());
    sprints.save(sprint);
  }
}
