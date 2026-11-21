package com.sprintflowinsight.workspace.customfield;

import com.sprintflowinsight.VersionPrecondition;
import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.workspace.activity.ActivityService;
import com.sprintflowinsight.workspace.customfield.CustomFieldViews.Definition;
import com.sprintflowinsight.workspace.customfield.CustomFieldViews.TaskValues;
import com.sprintflowinsight.workspace.project.ProjectAccessService;
import com.sprintflowinsight.workspace.project.ProjectMember;
import com.sprintflowinsight.workspace.project.ProjectMemberRepository;
import com.sprintflowinsight.workspace.project.ProjectMutationService;
import com.sprintflowinsight.workspace.task.Task;
import com.sprintflowinsight.workspace.task.TaskRepository;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomFieldService {
  private final CustomFieldRepository fields;
  private final CustomValueRepository values;
  private final ProjectAccessService access;
  private final ProjectMutationService mutations;
  private final TaskRepository tasks;
  private final ProjectMemberRepository members;
  private final ActivityService activity;
  private final EntityManager em;
  private final com.sprintflowinsight.common.audit.AuditService audit;

  public CustomFieldService(
      CustomFieldRepository fields,
      CustomValueRepository values,
      ProjectAccessService access,
      ProjectMutationService mutations,
      TaskRepository tasks,
      ProjectMemberRepository members,
      ActivityService activity,
      EntityManager em,
      com.sprintflowinsight.common.audit.AuditService audit) {
    this.audit = audit;
    this.fields = fields;
    this.values = values;
    this.access = access;
    this.mutations = mutations;
    this.tasks = tasks;
    this.members = members;
    this.activity = activity;
    this.em = em;
  }

  @Transactional(readOnly = true)
  public List<Definition> definitions(Long projectId, HttpSession session) {
    access.read(projectId, session);
    return fields.findByProjectIdOrderByIdAsc(projectId).stream().map(Definition::of).toList();
  }

  @Transactional
  public Definition create(Long projectId, FieldDefinitionRequest request, HttpSession session) {
    manageLock(projectId, session);
    if (fields.countByProjectId(projectId) >= FieldDefinitionRequest.MAX_FIELDS)
      throw ApiException.conflict("A project can have at most 20 custom fields.");
    var saved =
        fields.saveAndFlush(
            new CustomField(
                projectId, request.normalizedName(), request.kind(), request.normalizedOptions()));
    record(
        projectId,
        "CUSTOM_FIELD_CREATED",
        "Created a custom field.",
        session,
        "customField",
        saved.id());
    return Definition.of(saved);
  }

  @Transactional
  public Definition update(
      Long projectId,
      Long fieldId,
      FieldDefinitionRequest request,
      String ifMatch,
      HttpSession session) {
    manageLock(projectId, session);
    var field = field(projectId, fieldId);
    VersionPrecondition.require(ifMatch, field.version());
    if (request.kind() != field.kind())
      throw ApiException.conflict("Create a new field to change its type.");
    var options = request.normalizedOptions();
    for (String removed : field.options())
      if (!options.contains(removed) && values.optionInUse(projectId, fieldId, removed))
        throw ApiException.conflict("An option is still used by a task, including recycled tasks.");
    field.update(request.normalizedName(), options);
    var saved = fields.saveAndFlush(field);
    record(
        projectId,
        "CUSTOM_FIELD_UPDATED",
        "Updated a custom field.",
        session,
        "customField",
        fieldId);
    return Definition.of(saved);
  }

  @Transactional
  public void delete(Long projectId, Long fieldId, String ifMatch, HttpSession session) {
    manageLock(projectId, session);
    var field = field(projectId, fieldId);
    VersionPrecondition.require(ifMatch, field.version());
    fields.delete(field);
    fields.flush();
    record(
        projectId,
        "CUSTOM_FIELD_DELETED",
        "Deleted a custom field and its values.",
        session,
        "customField",
        fieldId);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public TaskValues taskValues(Long taskId, HttpSession session) {
    Task task = task(taskId);
    access.read(task.getProjectId(), session);
    return snapshot(task, values.forTask(task.getProjectId(), taskId));
  }

  @Transactional
  public TaskValues replaceValues(
      Long taskId, FieldValuesRequest request, String ifMatch, HttpSession session) {
    Task task = task(taskId);
    Long projectId = task.getProjectId();
    access.write(projectId, session);
    mutations.lock(projectId);
    access.forget(projectId);
    access.write(projectId, session);
    em.refresh(task);
    if (task.getTrashedAt() != null) throw ApiException.notFound("Task not found.");
    VersionPrecondition.require(ifMatch, task.getVersion());
    if (request == null
        || request.values() == null
        || request.values().size() > FieldDefinitionRequest.MAX_FIELDS)
      throw ApiException.badRequest("Provide a complete snapshot of at most 20 custom values.");
    Map<Long, CustomField> definitions =
        fields.findByProjectIdOrderByIdAsc(projectId).stream()
            .collect(Collectors.toMap(CustomField::id, field -> field));
    Map<Long, FieldValue> normalized = new HashMap<>();
    Set<Long> seen = new java.util.HashSet<>();
    for (var entry : request.values()) {
      if (entry == null || entry.fieldId() == null || !seen.add(entry.fieldId()))
        throw ApiException.badRequest("Provide each custom field at most once.");
      var field = definitions.get(entry.fieldId());
      if (field == null) throw ApiException.notFound("Custom field not found in this project.");
      FieldValue value = FieldValue.parse(field.kind(), field.options(), entry.value());
      if (value != null) normalized.put(entry.fieldId(), value);
    }
    if (normalized.values().stream().anyMatch(value -> value.memberId() != null)) {
      Set<Long> memberIds = memberIds(projectId);
      if (normalized.values().stream()
          .anyMatch(value -> value.memberId() != null && !memberIds.contains(value.memberId())))
        throw ApiException.badRequest("Choose a current member of this project.");
    }
    var previous = values.forTask(projectId, taskId);
    Map<Long, String> before =
        previous.stream()
            .collect(
                Collectors.toMap(
                    CustomValueRepository.Stored::fieldId, row -> row.value().value()));
    Map<Long, String> after =
        normalized.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().value()));
    if (before.equals(after)) return snapshot(task, previous);
    task.touchCustomFields();
    tasks.saveAndFlush(task);
    values.deleteForTask(projectId, taskId);
    normalized.forEach(
        (id, value) -> values.insert(projectId, taskId, id, definitions.get(id).kind(), value));
    record(
        projectId,
        "CUSTOM_VALUES_UPDATED",
        "Updated a task's custom fields.",
        session,
        "task",
        taskId);
    return snapshot(task, values.forTask(projectId, taskId));
  }

  @Transactional(readOnly = true)
  public PreparedFieldFilter prepare(
      Long projectId, FieldCondition condition, HttpSession session) {
    access.read(projectId, session);
    return prepareForProject(projectId, condition);
  }

  /** Rule callers have already authorized the project and hold its mutation lock. */
  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public PreparedFieldFilter prepareForProject(Long projectId, FieldCondition condition) {
    if (condition == null || condition.fieldId() == null)
      throw ApiException.badRequest("Choose a custom field for this condition.");
    var field = field(projectId, condition.fieldId());
    FieldValue expected = condition.expected(field.kind(), field.options());
    if (expected != null
        && expected.memberId() != null
        && !memberIds(projectId).contains(expected.memberId()))
      throw ApiException.badRequest("Choose a current member of this project.");
    return new PreparedFieldFilter(
        projectId, field.id(), field.kind(), condition.operator(), expected);
  }

  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public boolean matchesProjectTask(Long projectId, Long taskId, FieldCondition condition) {
    return values.matchesTask(projectId, taskId, prepareForProject(projectId, condition));
  }

  @Transactional(readOnly = true)
  public CustomFieldViews.Matches matching(
      Long projectId, FieldCondition condition, HttpSession session) {
    var filter = prepare(projectId, condition, session);
    return new CustomFieldViews.Matches(values.matchingTaskIds(projectId, filter));
  }

  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public Stream<CustomFieldViews.TaskValueRow> streamForExport(
      Long projectId, HttpSession session) {
    access.read(projectId, session);
    return values
        .forExport(projectId)
        .map(row -> new CustomFieldViews.TaskValueRow(row.taskId(), row.view()));
  }

  private CustomField field(Long projectId, Long fieldId) {
    return fields
        .findByIdAndProjectId(fieldId, projectId)
        .orElseThrow(() -> ApiException.notFound("Custom field not found in this project."));
  }

  private Task task(Long taskId) {
    return tasks
        .findById(taskId)
        .filter(task -> task.getTrashedAt() == null)
        .orElseThrow(() -> ApiException.notFound("Task not found."));
  }

  private Set<Long> memberIds(Long projectId) {
    return members.findByProjectId(projectId).stream()
        .map(ProjectMember::getId)
        .collect(Collectors.toSet());
  }

  private void manageLock(Long projectId, HttpSession session) {
    access.adminOrOwner(projectId, session);
    mutations.lock(projectId);
    access.forget(projectId);
    access.adminOrOwner(projectId, session);
  }

  private TaskValues snapshot(Task task, List<CustomValueRepository.Stored> rows) {
    return new TaskValues(
        task.getId(),
        task.getVersion(),
        rows.stream().map(CustomValueRepository.Stored::view).toList());
  }

  private void record(
      Long projectId, String type, String summary, HttpSession session, String entity, Long id) {
    activity.record(projectId, type, summary, access.email(session), entity, id, Instant.now());
    if (type.startsWith("CUSTOM_FIELD_")) {
      var caller =
          access.require(
              projectId,
              session,
              com.sprintflowinsight.workspace.project.AccessContext.Need.MANAGE);
      audit.record(
          projectId, caller.userId(), caller.role().label(), type, "custom_field", id, Map.of());
    }
  }
}
