package com.sprintflowinsight.workspace.project;

import com.sprintflowinsight.workspace.activity.ActivityService;
import com.sprintflowinsight.workspace.sprint.Sprint;
import com.sprintflowinsight.workspace.sprint.SprintRepository;
import com.sprintflowinsight.workspace.story.UserStory;
import com.sprintflowinsight.workspace.story.UserStoryRepository;
import com.sprintflowinsight.workspace.task.Task;
import com.sprintflowinsight.workspace.task.TaskService;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@org.springframework.stereotype.Service
public class ProjectTemplateService {
  private final ProjectRepository projects;
  private final ProjectMemberRepository members;
  private final ProjectAccessService access;
  private final SprintRepository sprints;
  private final UserStoryRepository stories;
  private final TaskService tasks;
  private final ActivityService activity;
  private final ProjectFactory factory;

  public ProjectTemplateService(
      ProjectRepository projects,
      ProjectMemberRepository members,
      ProjectAccessService access,
      SprintRepository sprints,
      UserStoryRepository stories,
      TaskService tasks,
      ActivityService activity,
      ProjectFactory factory) {
    this.factory = factory;
    this.projects = projects;
    this.members = members;
    this.access = access;
    this.sprints = sprints;
    this.stories = stories;
    this.tasks = tasks;
    this.activity = activity;
  }

  /** Runs in its own transaction, retried when a concurrent creation took the derived key. */
  public Project create(TemplateRequest request, HttpSession session) {
    if (!Set.of("Blank", "Scrum", "Kanban").contains(request.template()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid project template.");
    String email = access.email(session);
    return factory.inTransactionRetryingDerivedKey(
        request.normalizedKey(), () -> createOnce(request, email));
  }

  private Project createOnce(TemplateRequest request, String email) {
    boolean sv = "SV".equals(request.language());
    String name = request.name().trim();
    Project project =
        factory.create(
            name,
            request.description(),
            email,
            request.normalizedKey(),
            "Created project “" + name + "” from " + request.template() + " template.");
    Long sprintId = null, storyId = null;
    if (request.template().equals("Scrum")) {
      Sprint sprint =
          new Sprint(
              "Sprint 1",
              sv ? "Leverera den första användbara delen" : "Deliver the first usable increment",
              "Planned",
              8);
      sprint.setProjectId(project.getId());
      LocalDate start = LocalDate.now(java.time.ZoneId.of("Europe/Stockholm"));
      sprint.setStartDate(start);
      sprint.setEndDate(start.plusDays(13));
      sprint.setCreatedAt(java.time.Instant.now());
      sprintId = sprints.save(sprint).getId();
      UserStory story =
          new UserStory(
              sv ? "Första leveransen" : "First delivery",
              sv ? "Definiera den första leveransen" : "Define the first increment",
              sv
                  ? "Teamet granskar en användbar leverans"
                  : "A usable increment is reviewed by the team",
              8,
              1,
              project.getId());
      story.setSprintId(sprintId);
      story.setBusinessValue(5);
      storyId = stories.save(story).getId();
    }
    if (!request.template().equals("Blank")) {
      String[] titles =
          request.template().equals("Scrum")
              ? (sv
                  ? new String[] {
                    "Enas om sprintmålet", "Bygg den första leveransen", "Granska leveransen"
                  }
                  : new String[] {
                    "Agree on the sprint goal", "Build the first increment", "Review the delivery"
                  })
              : (sv
                  ? new String[] {
                    "Fånga nästa idé", "Börja fokuserat arbete", "Granska färdigt arbete"
                  }
                  : new String[] {
                    "Capture the next idea", "Start focused work", "Review completed work"
                  });
      for (String title : titles) {
        Task task =
            new Task(
                title,
                sv
                    ? "Anpassa den här startuppgiften till ditt team."
                    : "Customize this starter task for your team.",
                "To Do");
        task.setStoryPoints(
            request.template().equals("Scrum")
                ? new int[] {1, 5, 2}[java.util.Arrays.asList(titles).indexOf(title)]
                : 1);
        task.setProjectId(project.getId());
        task.setSprintId(sprintId);
        task.setStoryId(storyId);
        tasks.createTask(task, email);
      }
    }
    return project;
  }
}
