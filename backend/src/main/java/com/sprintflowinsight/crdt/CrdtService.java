package com.sprintflowinsight.crdt;

import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.workspace.events.TaskUpdated;
import com.sprintflowinsight.workspace.events.WorkspaceChanged;
import com.sprintflowinsight.workspace.project.AccessContext.Need;
import com.sprintflowinsight.workspace.project.ProjectAccessService;
import com.sprintflowinsight.workspace.project.ProjectMutationService;
import com.sprintflowinsight.workspace.project.ProjectRole;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CrdtService {
  private final CrdtStore store;
  private final ProjectAccessService access;
  private final ProjectMutationService mutations;
  private final JdbcTemplate jdbc;
  private final ApplicationEventPublisher events;

  CrdtService(
      CrdtStore store,
      ProjectAccessService access,
      ProjectMutationService mutations,
      JdbcTemplate jdbc,
      ApplicationEventPublisher events) {
    this.store = store;
    this.access = access;
    this.mutations = mutations;
    this.jdbc = jdbc;
    this.events = events;
  }

  record Resource(String text, String author) {}

  record Update(long revision, byte[] bytes) {}

  record Snapshot(
      boolean active,
      boolean editable,
      String initialText,
      int maxLength,
      long revision,
      long projectionRevision,
      long snapshotRevision,
      byte[] snapshot,
      List<Update> updates) {}

  record Metadata(boolean active) {}

  public record Append(long revision, boolean duplicate) {}

  private record Editor(ProjectRole role, String email) {}

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Snapshot read(
      long projectId, CrdtTarget target, long id, long after, HttpSession session) {
    var caller = access.require(projectId, session, Need.READ);
    var resource = resource(projectId, target, id);
    boolean editable = !caller.archived() && canEdit(resource, caller.role(), caller.email());
    return store
        .find(projectId, target, id, false)
        .map(doc -> state(doc, target, editable, after))
        .orElseGet(
            () ->
                new Snapshot(
                    false, editable, resource.text(), target.maxLength, 0, 0, 0, null, List.of()));
  }

  @Transactional(readOnly = true)
  public Metadata metadata(long projectId, CrdtTarget target, long id, HttpSession session) {
    access.require(projectId, session, Need.READ);
    resource(projectId, target, id);
    return new Metadata(store.exists(projectId, target, id));
  }

  @Transactional
  public Snapshot activate(long projectId, CrdtTarget target, long id, HttpSession session) {
    access.write(projectId, session);
    mutations.lock(projectId);
    access.forget(projectId);
    var caller = access.require(projectId, session, Need.WRITE);
    var resource = resource(projectId, target, id);
    if (!canEdit(resource, caller.role(), caller.email())) throw denied();
    return state(store.open(projectId, target, id, resource.text()), target, true, 0);
  }

  @Transactional
  public Append appendFromSocket(
      long projectId, CrdtTarget target, long id, long userId, UUID updateId, byte[] bytes) {
    editor(projectId, userId);
    mutations.lock(projectId);
    var editor = editor(projectId, userId);
    if (!canEdit(resource(projectId, target, id), editor.role(), editor.email())) throw denied();
    var doc = openDoc(projectId, target, id);
    try {
      var append = store.append(doc, updateId, bytes);
      return new Append(append.revision(), append.duplicate());
    } catch (IllegalArgumentException error) {
      throw ApiException.badRequest(error.getMessage());
    } catch (IllegalStateException error) {
      throw ApiException.conflict(error.getMessage());
    }
  }

  @Transactional
  public void project(
      long projectId, CrdtTarget target, long id, long revision, String text, HttpSession session) {
    access.write(projectId, session);
    mutations.lock(projectId);
    access.forget(projectId);
    var caller = access.require(projectId, session, Need.WRITE);
    var resource = resource(projectId, target, id);
    if (!canEdit(resource, caller.role(), caller.email())) throw denied();
    var doc = openDoc(projectId, target, id);
    if (revision <= doc.projectionRevision()) return;
    if (revision != doc.revision())
      throw ApiException.conflict("Refresh the collaboration document before saving its text.");
    if (text.length() > target.maxLength || (target == CrdtTarget.RETROSPECTIVE && text.isBlank()))
      throw ApiException.badRequest("The shared text is too long or required by this entry.");
    if (!text.equals(resource.text())) {
      if (target == CrdtTarget.TASK) {
        Long version =
            jdbc.queryForObject(
                "update task set description=?,version=coalesce(version,0)+1"
                    + " where project_id=? and id=? returning version",
                Long.class,
                text,
                projectId,
                id);
        events.publishEvent(
            new TaskUpdated(
                UUID.randomUUID(), projectId, caller.userId(), Instant.now(), id, version));
      } else {
        jdbc.update(
            "update retrospective_entry set message=?,version=coalesce(version,0)+1 where"
                + " project_id=? and id=?",
            text,
            projectId,
            id);
        events.publishEvent(
            new WorkspaceChanged(
                UUID.randomUUID(),
                projectId,
                caller.userId(),
                Instant.now(),
                "RetrospectiveChanged",
                "retrospective",
                id));
      }
    }
    store.projected(doc, revision);
  }

  @Transactional(readOnly = true)
  public String participant(long projectId, CrdtTarget target, long id, long userId) {
    var editor = editor(projectId, userId);
    if (!canEdit(resource(projectId, target, id), editor.role(), editor.email())) throw denied();
    if (store.find(projectId, target, id, false).isEmpty())
      throw ApiException.notFound("Collaboration is unavailable.");
    return editor.email();
  }

  private CrdtStore.Doc openDoc(long projectId, CrdtTarget target, long id) {
    return store
        .find(projectId, target, id, true)
        .orElseThrow(() -> ApiException.notFound("Collaboration is unavailable."));
  }

  private Snapshot state(CrdtStore.Doc doc, CrdtTarget target, boolean editable, long after) {
    boolean needsSnapshot = after < doc.snapshotRevision();
    var tail =
        store.after(doc.id(), Math.max(after, doc.snapshotRevision())).stream()
            .map(item -> new Update(item.revision(), item.bytes()))
            .toList();
    return new Snapshot(
        true,
        editable,
        doc.initialText(),
        target.maxLength,
        doc.revision(),
        doc.projectionRevision(),
        doc.snapshotRevision(),
        needsSnapshot ? doc.snapshot() : null,
        tail);
  }

  private Resource resource(long projectId, CrdtTarget target, long id) {
    String query =
        switch (target) {
          case TASK ->
              "select coalesce(description,''),null from task where project_id=? and id=? and"
                  + " trashed_at is null";
          case RETROSPECTIVE ->
              """
              select r.message,r.author_email from retrospective_entry r
              join sprint s on s.id=r.sprint_id and s.project_id=r.project_id
              where r.project_id=? and r.id=? and s.trashed_at is null
              """;
        };
    return jdbc
        .query(
            query, (row, index) -> new Resource(row.getString(1), row.getString(2)), projectId, id)
        .stream()
        .findFirst()
        .orElseThrow(() -> ApiException.notFound("The collaboration resource could not be found."));
  }

  private Editor editor(long projectId, long userId) {
    return jdbc
        .query(
            """
select m.role,u.email from projects p join project_member m on m.project_id=p.id
join users u on u.id=m.user_id where p.id=? and u.id=? and p.trashed_at is null and p.archived_at is null
""",
            (row, index) -> new Editor(ProjectRole.fromLabel(row.getString(1)), row.getString(2)),
            projectId,
            userId)
        .stream()
        .findFirst()
        .filter(item -> item.role().canWrite())
        .orElseThrow(CrdtService::denied);
  }

  private static boolean canEdit(Resource resource, ProjectRole role, String email) {
    return role.canWrite()
        && (resource.author() == null
            || role.canManage()
            || resource.author().equalsIgnoreCase(email));
  }

  private static ApiException denied() {
    return ApiException.forbidden("You do not have permission to edit this shared text.");
  }
}
