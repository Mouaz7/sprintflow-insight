package com.sprintflowinsight.workspace.transfer;

import com.sprintflowinsight.common.audit.AuditService;
import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.workspace.customfield.*;
import com.sprintflowinsight.workspace.project.*;
import com.sprintflowinsight.workspace.task.*;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

@Service
public class ImportService {
  private final ProjectAccessService access;
  private final ProjectMutationService mutations;
  private final ProjectMemberRepository members;
  private final CustomFieldService fields;
  private final TaskApplicationService application;
  private final TaskRepository tasks;
  private final TaskStatusHistoryRepository histories;
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final Validator validator;
  private final AuditService audit;

  public ImportService(
      ProjectAccessService access,
      ProjectMutationService mutations,
      ProjectMemberRepository members,
      CustomFieldService fields,
      TaskApplicationService application,
      TaskRepository tasks,
      TaskStatusHistoryRepository histories,
      JdbcTemplate jdbc,
      ObjectMapper json,
      Validator validator,
      AuditService audit) {
    this.access = access;
    this.mutations = mutations;
    this.members = members;
    this.fields = fields;
    this.application = application;
    this.tasks = tasks;
    this.histories = histories;
    this.jdbc = jdbc;
    this.json = json;
    this.validator = validator;
    this.audit = audit;
  }

  record Plan(ImportViews.Preview view, String requestHash, ImportTable table) {}

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public ImportViews.Preview preview(Long projectId, ImportRequest request, HttpSession session) {
    authorize(projectId, request, session);
    return prepare(projectId, request, session).view();
  }

  private void authorize(Long projectId, ImportRequest request, HttpSession session) {
    access.write(projectId, session);
    if (request.preserveHistory()) access.adminOrOwner(projectId, session);
  }

  private Plan prepare(Long projectId, ImportRequest request, HttpSession session) {
    ImportTable table =
        request.format() == ImportFormat.CSV
            ? CsvImportParser.parse(request.content())
            : JiraImportParser.parse(request.content());
    var definitions = fields.definitions(projectId, session);
    var projectMembers =
        members.findByProjectId(projectId).stream()
            .sorted(Comparator.comparing(ProjectMember::getId))
            .toList();
    var mapping = ImportMapper.mapping(request, table, definitions);
    String requestHash =
        sha(
            json.writeValueAsString(
                List.of(
                    projectId,
                    request.format(),
                    request.content(),
                    mapping,
                    new TreeMap<>(request.statuses()),
                    request.truncateText(),
                    request.preserveHistory())));
    String fingerprint =
        sha(
            json.writeValueAsString(
                List.of(
                    requestHash,
                    definitions,
                    projectMembers.stream()
                        .map(m -> List.of(m.getId(), m.getUserId(), m.getEmail(), m.getRole()))
                        .toList())));
    var rows = new ArrayList<ImportViews.Row>();
    var seen = new HashSet<String>();
    Instant now = Instant.now();
    Set<String> keys = new HashSet<>();
    for (int i = 0; i < table.rows().size(); i++) {
      var row =
          ImportMapper.row(
              i + 1, table.rows().get(i), mapping, request, definitions, projectMembers, now);
      if (row.sourceKey() != null) {
        if (!seen.add(row.sourceKey())) {
          var errors = new ArrayList<>(row.errors());
          errors.add("A source key occurs more than once in this file.");
          row = copy(row, errors, false);
        }
        keys.add(row.sourceKey());
      }
      rows.add(row);
    }
    Set<String> existing =
        keys.isEmpty()
            ? Set.of()
            : new HashSet<>(
                new NamedParameterJdbcTemplate(jdbc)
                    .queryForList(
                        "select source_key from import_source where project_id=:p and format=:f and"
                            + " source_key in (:keys)",
                        Map.of("p", projectId, "f", request.format().name(), "keys", keys),
                        String.class));
    rows.replaceAll(
        r -> copy(r, r.errors(), r.sourceKey() != null && existing.contains(r.sourceKey())));
    int create = (int) rows.stream().filter(r -> !r.duplicate()).count();
    int skip = rows.size() - create;
    UUID previous =
        jdbc
            .query(
                "select id from import_batch where project_id=? and request_hash=?",
                (r, n) -> r.getObject(1, UUID.class),
                projectId,
                requestHash)
            .stream()
            .findFirst()
            .orElse(null);
    return new Plan(
        new ImportViews.Preview(
            request.format(),
            table.columns(),
            mapping,
            List.copyOf(rows),
            fingerprint,
            create,
            skip,
            rows.stream().allMatch(r -> r.errors().isEmpty()),
            previous),
        requestHash,
        table);
  }

  @Transactional
  public ImportViews.Result commit(Long projectId, ImportRequest request, HttpSession session) {
    authorize(projectId, request, session);
    mutations.lock(projectId);
    access.forget(projectId);
    authorize(projectId, request, session);
    var plan = prepare(projectId, request, session);
    var preview = plan.view();
    if (preview.previousBatchId() != null)
      return receipt(projectId, preview.previousBatchId(), true);
    if (request.expectedFingerprint() == null
        || !MessageDigest.isEqual(
            preview.fingerprint().getBytes(StandardCharsets.US_ASCII),
            request.expectedFingerprint().getBytes(StandardCharsets.US_ASCII)))
      throw ApiException.conflict(
          "Preview this file again after changing its mapping, members or custom fields.");
    if (!preview.canCommit())
      throw ApiException.badRequest(
          "Correct every preview error before importing. Nothing was saved.");
    UUID batchId = UUID.randomUUID();
    var ids = new ArrayList<Long>();
    jdbc.update(
        "insert into"
            + " import_batch(id,project_id,actor_user_id,request_hash,format,created_count,skipped_count,task_ids)"
            + " values(?,?,?,?,?,?,?,cast('[]' as jsonb))",
        batchId,
        projectId,
        access.userId(session),
        plan.requestHash(),
        request.format().name(),
        preview.createCount(),
        preview.skipCount());
    for (var row : preview.rows()) {
      if (row.duplicate()) continue;
      TaskRequest taskRequest =
          new TaskRequest(
              row.title(),
              row.description(),
              row.status(),
              false,
              null,
              projectId,
              null,
              null,
              row.assigneeEmail(),
              row.storyPoints(),
              row.dueAt(),
              row.labels(),
              List.of());
      if (!validator.validate(taskRequest).isEmpty())
        throw ApiException.badRequest("A mapped task is invalid. Nothing was saved.");
      var response = application.createTask(taskRequest, session);
      if (!(response.getBody() instanceof Task task) || !response.getStatusCode().is2xxSuccessful())
        throw ApiException.badRequest("A task association changed. Preview the file again.");
      tasks.flush();
      if (request.preserveHistory()) preserve(task, row, access.email(session));
      if (!row.customValues().isEmpty())
        fields.replaceValues(
            task.getId(),
            new FieldValuesRequest(
                row.customValues().entrySet().stream()
                    .map(e -> new FieldValuesRequest.Entry(e.getKey(), e.getValue()))
                    .toList()),
            "\"" + task.getVersion() + "\"",
            session);
      var raw = plan.table().rows().get(row.rowNumber() - 1);
      jdbc.update(
          "insert into"
              + " import_source(task_id,project_id,batch_id,format,source_key,source_created_at,source_started_at,source_completed_at,original_title,original_description,history_preserved)"
              + " values(?,?,?,?,?,?,?,?,?,?,?)",
          task.getId(),
          projectId,
          batchId,
          request.format().name(),
          row.sourceKey(),
          timestamp(row.createdAt()),
          timestamp(row.startedAt()),
          timestamp(row.completedAt()),
          raw.getOrDefault(preview.mapping().getOrDefault("title", ""), ""),
          raw.getOrDefault(preview.mapping().getOrDefault("description", ""), ""),
          request.preserveHistory());
      ids.add(task.getId());
    }
    jdbc.update(
        "update import_batch set task_ids=cast(? as jsonb) where id=?",
        json.writeValueAsString(ids),
        batchId);
    audit.record(
        projectId,
        access.userId(session),
        access.require(projectId, session, AccessContext.Need.WRITE).role().label(),
        "IMPORT_COMPLETED",
        "import_batch",
        batchId,
        Map.of(
            "created",
            ids.size(),
            "skipped",
            preview.skipCount(),
            "historical",
            request.preserveHistory()));
    return new ImportViews.Result(
        batchId, ids.size(), preview.skipCount(), List.copyOf(ids), false);
  }

  private void preserve(Task task, ImportViews.Row row, String actor) {
    task.setCreatedAt(row.createdAt());
    task.setStartedAt(row.startedAt());
    task.setCompletedAt(row.completedAt());
    tasks.saveAndFlush(task);
    histories.deleteByTaskId(task.getId());
    history(task, "To Do", row.createdAt(), actor);
    if (row.startedAt() != null) history(task, "In Progress", row.startedAt(), actor);
    if (row.completedAt() != null) history(task, "Done", row.completedAt(), actor);
    else if (!"To Do".equals(row.status())
        && !("In Progress".equals(row.status()) && row.startedAt() != null))
      history(
          task, row.status(), row.startedAt() == null ? row.createdAt() : row.startedAt(), actor);
  }

  private void history(Task task, String status, Instant at, String actor) {
    var h = new TaskStatusHistory();
    h.setProjectId(task.getProjectId());
    h.setTaskId(task.getId());
    h.setStatus(status);
    h.setChangedAt(at);
    h.setStoryPoints(task.getStoryPoints());
    h.setActorEmail(actor);
    histories.save(h);
  }

  @Transactional(readOnly = true)
  public List<ImportViews.Batch> batches(Long projectId, HttpSession session) {
    access.read(projectId, session);
    return jdbc.query(
        "select id,format,created_count,skipped_count,created_at from import_batch where"
            + " project_id=? order by created_at desc,id desc limit 50",
        (r, n) ->
            new ImportViews.Batch(
                r.getObject(1, UUID.class),
                r.getString(2),
                r.getInt(3),
                r.getInt(4),
                r.getTimestamp(5).toInstant()),
        projectId);
  }

  private ImportViews.Result receipt(Long projectId, UUID id, boolean replayed) {
    return jdbc.queryForObject(
        "select created_count,skipped_count,task_ids::text from import_batch where project_id=? and"
            + " id=?",
        (r, n) ->
            new ImportViews.Result(
                id,
                r.getInt(1),
                r.getInt(2),
                json.readValue(
                    r.getString(3), new tools.jackson.core.type.TypeReference<List<Long>>() {}),
                replayed),
        projectId,
        id);
  }

  private static Object timestamp(Instant at) {
    return at == null ? null : java.sql.Timestamp.from(at);
  }

  private static String sha(String input) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static ImportViews.Row copy(ImportViews.Row r, List<String> errors, boolean duplicate) {
    return new ImportViews.Row(
        r.rowNumber(),
        r.title(),
        r.description(),
        r.status(),
        r.storyPoints(),
        r.assigneeEmail(),
        r.labels(),
        r.dueAt(),
        r.sourceKey(),
        r.createdAt(),
        r.startedAt(),
        r.completedAt(),
        r.customValues(),
        List.copyOf(errors),
        r.warnings(),
        duplicate);
  }
}
