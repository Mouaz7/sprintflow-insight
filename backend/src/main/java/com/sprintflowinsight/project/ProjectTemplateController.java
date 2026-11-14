package com.sprintflowinsight.project;
import com.sprintflowinsight.model.*;
import com.sprintflowinsight.repository.SprintRepository;
import com.sprintflowinsight.service.TaskService;
import com.sprintflowinsight.story.*;
import com.sprintflowinsight.service.ActivityService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.Set;
@RestController @RequestMapping("/api/projects/templates")
public class ProjectTemplateController {
 private final ProjectRepository projects; private final ProjectMemberRepository members; private final ProjectAccessService access; private final SprintRepository sprints; private final UserStoryRepository stories; private final TaskService tasks; private final ActivityService activity;
 public ProjectTemplateController(ProjectRepository projects,ProjectMemberRepository members,ProjectAccessService access,SprintRepository sprints,UserStoryRepository stories,TaskService tasks,ActivityService activity){this.projects=projects;this.members=members;this.access=access;this.sprints=sprints;this.stories=stories;this.tasks=tasks;this.activity=activity;}
 @PostMapping @Transactional public Project create(@Valid @RequestBody TemplateRequest request,HttpSession session){if(!Set.of("Blank","Scrum","Kanban").contains(request.template()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a valid project template.");boolean sv="SV".equals(request.language());String email=access.email(session);String name=request.name().trim();if(projects.findByName(name).isPresent())throw new ResponseStatusException(HttpStatus.CONFLICT,"A project with this name already exists.");Project project=projects.save(new Project(name,request.description(),email));ProjectMember owner=new ProjectMember();owner.setProjectId(project.getId());owner.setEmail(email);owner.setRole("Owner");members.save(owner);Long sprintId=null,storyId=null;
  if(request.template().equals("Scrum")){Sprint sprint=new Sprint("Sprint 1",sv?"Leverera den första användbara delen":"Deliver the first usable increment","Planned",8);sprint.setProjectId(project.getId());LocalDate start=LocalDate.now(java.time.ZoneId.of("Europe/Stockholm"));sprint.setStartDate(start);sprint.setEndDate(start.plusDays(13));sprint.setCreatedAt(java.time.Instant.now());sprintId=sprints.save(sprint).getId();UserStory story=new UserStory(sv?"Första leveransen":"First delivery",sv?"Definiera den första leveransen":"Define the first increment",sv?"Teamet granskar en användbar leverans":"A usable increment is reviewed by the team",8,1,project.getId());story.setSprintId(sprintId);story.setBusinessValue(5);storyId=stories.save(story).getId();}
  if(!request.template().equals("Blank")){String[] titles=request.template().equals("Scrum")?(sv?new String[]{"Enas om sprintmålet","Bygg den första leveransen","Granska leveransen"}:new String[]{"Agree on the sprint goal","Build the first increment","Review the delivery"}):(sv?new String[]{"Fånga nästa idé","Börja fokuserat arbete","Granska färdigt arbete"}:new String[]{"Capture the next idea","Start focused work","Review completed work"});for(String title:titles){Task task=new Task(title,sv?"Anpassa den här startuppgiften till ditt team.":"Customize this starter task for your team.","To Do");task.setStoryPoints(request.template().equals("Scrum")?new int[]{1,5,2}[java.util.Arrays.asList(titles).indexOf(title)]:1);task.setProjectId(project.getId());task.setSprintId(sprintId);task.setStoryId(storyId);tasks.createTask(task,email);}}
  activity.record(project.getId(),"PROJECT_CREATED","Created project “"+name+"” from "+request.template()+" template.",email);return project;
 }
 public record TemplateRequest(@NotBlank @Size(max=255) String name,@Size(max=255) String description,@NotBlank String template,String language){}
}
