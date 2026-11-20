package com.sprintflowinsight.portability;

import com.sprintflowinsight.auth.SessionUser;
import com.sprintflowinsight.common.audit.AuditService;
import com.sprintflowinsight.common.error.ApiException;
import jakarta.servlet.http.HttpSession;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.zip.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/** Explicit, allow-listed data projection; authentication records are never serialized. */
@Service
public class AccountExportService {
  private static final String VISIBLE =
      " in (select m.project_id from project_member m join projects p on p.id=m.project_id where"
          + " m.user_id=? and p.trashed_at is null)";
  private final java.util.concurrent.Semaphore exporting = new java.util.concurrent.Semaphore(2);
  private final SessionUser sessionUser;
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final AuditService audit;
  private final long maxBytes;

  public AccountExportService(
      SessionUser sessionUser,
      JdbcTemplate jdbc,
      ObjectMapper json,
      AuditService audit,
      @Value("${app.account-export.max-bytes:67108864}") long maxBytes) {
    this.sessionUser = sessionUser;
    this.jdbc = jdbc;
    this.json = json;
    this.audit = audit;
    if (maxBytes < 1048576 || maxBytes > 134217728)
      throw new IllegalArgumentException("Account export limit must be 1–128 MiB.");
    this.maxBytes = maxBytes;
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ, rollbackFor = IOException.class)
  public byte[] export(HttpSession session) {
    Long id = sessionUser.id(session);
    String email = sessionUser.email(session);
    // Keep the exporting identity alive until this snapshot and its audit record commit.
    if (jdbc.queryForList("select id from users where id=? for key share", Long.class, id)
        .isEmpty()) throw ApiException.notFound("Account not found.");
    if (!exporting.tryAcquire())
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS, "Account exports are busy. Try again shortly.");
    try (var archive = new Archive(maxBytes)) {
      archive.rows(
          "account.json",
          "select id,email,display_name,job_title,bio,avatar_image from users where id=?",
          id);
      archive.rows(
          "preferences.json",
          "select language,theme,notifications,onboarding_completed,saved_filters::jsonb as"
              + " saved_filters from user_preferences where user_id=?",
          id);
      archive.rows(
          "email-summaries.json",
          "select frequency,time_zone,next_due_at,checkpoint_at,last_sent_at from"
              + " digest_subscription where user_id=?",
          id);
      archive.rows(
          "dashboard-layouts.json",
          "select project_id,widgets,version from dashboard_layout where user_id=? order by"
              + " project_id",
          id);
      archive.rows(
          "memberships.json",
          "select project_id,role from project_member where user_id=? order by project_id",
          id);
      archive.rows(
          "own-comments.json",
          "select id,task_id,project_id,message,created_at,version from task_comment where"
              + " author_email=? order by id",
          email);
      archive.rows(
          "own-retrospectives.json",
          "select id,project_id,sprint_id,category,message,created_at,version from"
              + " retrospective_entry where author_email=? order by id",
          email);
      archive.rows(
          "own-time.json",
          "select id,project_id,task_id,minutes,spent_on,note,started_at,version from time_entry"
              + " where user_id=? order by id",
          id);
      archive.rows(
          "own-capacity.json",
          "select"
              + " project_id,sprint_id,minutes_per_day,availability_percent,point_capacity,days_off,updated_at,version"
              + " from sprint_capacity where user_id=? order by id",
          id);
      archive.rows(
          "own-notifications.json",
          "select id,project_id,message,is_read,created_at from notification where"
              + " recipient_email=? order by id",
          email);
      archive.rows(
          "own-audit.json",
          "select id,project_id,actor_role,action,target_type,target_id,details,created_at from"
              + " admin_audit_event where actor_user_id=? order by id",
          id);
      archive.rows(
          "own-poker-votes.json",
          "select session_id,round,card from poker_vote where user_id=? order by session_id,round",
          id);
      archive.rows(
          "own-reports.json",
          "select id,project_id,created_at,expires_at from shared_report where created_by=? order"
              + " by id",
          email);
      archive.rows(
          "projects.json",
          "select id,key,name,description,owner_email,archived_at from projects where id"
              + VISIBLE
              + " order by id",
          id);
      archive.rows(
          "members.json",
          "select project_id,id,user_id,email,role from project_member where project_id"
              + VISIBLE
              + " order by project_id,id",
          id);
      archive.rows(
          "tasks.json",
          "select"
              + " id,project_id,number,title,description,status,is_blocked,block_reason,sprint_id,story_id,assignee_email,story_points,created_at,started_at,completed_at,due_at,estimate_minutes,labels::jsonb"
              + " as labels,checklist::jsonb as checklist,version from task where trashed_at is"
              + " null and project_id"
              + VISIBLE
              + " order by project_id,id",
          id);
      archive.rows(
          "stories.json",
          "select"
              + " id,project_id,title,description,acceptance_criteria,story_points,priority_id,business_value,sprint_id,epic_id,version"
              + " from user_stories where trashed_at is null and project_id"
              + VISIBLE
              + " order by project_id,id",
          id);
      archive.rows(
          "sprints.json",
          "select"
              + " id,project_id,name,goal,status,total_story_points,start_date,end_date,completed_at,version"
              + " from sprint where trashed_at is null and project_id"
              + VISIBLE
              + " order by project_id,id",
          id);
      archive.rows(
          "epics.json",
          "select id,project_id,title,description,color,start_date,target_date,version from epic"
              + " where project_id"
              + VISIBLE
              + " order by project_id,id",
          id);
      archive.rows(
          "custom-fields.json",
          "select id,project_id,name,kind,options,version from custom_field where project_id"
              + VISIBLE
              + " order by project_id,id",
          id);
      archive.rows(
          "custom-values.json",
          "select v.project_id,v.task_id,v.field_id,v.kind,v.text_value,v.number_value::text as"
              + " number_value,v.date_value,v.member_id from task_custom_value v join task t on"
              + " t.id=v.task_id where t.trashed_at is null and v.project_id"
              + VISIBLE
              + " order by v.project_id,v.task_id,v.field_id",
          id);
      archive.rows(
          "workflow-history.json",
          "select h.id,h.project_id,h.task_id,h.sprint_id,h.status,h.changed_at,h.story_points from"
              + " task_status_history h join task t on t.id=h.task_id where t.trashed_at is null"
              + " and h.project_id"
              + VISIBLE
              + " order by h.project_id,h.id",
          id);
      archive.rows(
          "workspace-comments.json",
          "select c.id,c.project_id,c.task_id,c.author_email,c.message,c.created_at,c.version from"
              + " task_comment c join task t on t.id=c.task_id where t.trashed_at is null and"
              + " c.project_id"
              + VISIBLE
              + " order by c.project_id,c.id",
          id);
      archive.rows(
          "workspace-retrospectives.json",
          "select id,project_id,sprint_id,author_email,category,message,created_at,version from"
              + " retrospective_entry where project_id"
              + VISIBLE
              + " order by project_id,id",
          id);
      archive.rows(
          "activity.json",
          "select"
              + " id,project_id,event_type,summary,actor_email,actor_user_id,entity_type,entity_id,created_at"
              + " from activity_event where project_id"
              + VISIBLE
              + " order by project_id,id",
          id);
      archive.rows(
          "dependencies.json",
          "select project_id,blocker_task_id,blocked_task_id from task_dependency where project_id"
              + VISIBLE
              + " order by project_id,blocker_task_id,blocked_task_id",
          id);
      archive.rows(
          "wip-limits.json",
          "select project_id,status,wip_limit from project_wip_limit where project_id"
              + VISIBLE
              + " order by project_id,status",
          id);
      archive.rows(
          "poker-sessions.json",
          "select"
              + " id,project_id,story_id,deck,cards,status,round,revealed_round,final_value,final_story_points,created_at,closed_at,version"
              + " from poker_session where project_id"
              + VISIBLE
              + " order by project_id,id",
          id);
      archive.rows(
          "revealed-poker-votes.json",
          "select v.session_id,v.round,v.user_id,v.card from poker_vote v join poker_session s on"
              + " s.id=v.session_id where v.round<=s.revealed_round and s.project_id"
              + VISIBLE
              + " order by v.session_id,v.round,v.user_id",
          id);
      archive.rows(
          "import-batches.json",
          "select id,project_id,format,created_count,skipped_count,task_ids,created_at from"
              + " import_batch where project_id"
              + VISIBLE
              + " order by project_id,created_at,id",
          id);
      archive.rows(
          "import-provenance.json",
          "select"
              + " s.task_id,s.project_id,s.batch_id,s.format,s.source_key,s.source_created_at,s.source_started_at,s.source_completed_at,s.original_title,s.original_description,s.history_preserved"
              + " from import_source s join task t on t.id=s.task_id where t.trashed_at is null and"
              + " s.project_id"
              + VISIBLE
              + " order by s.project_id,s.task_id",
          id);
      archive.rows(
          "attachments.json",
          "select a.id,a.project_id,a.task_id,a.name,a.url,a.created_at,case when a.storage_key is"
              + " null then null else 'files/'||a.id end as archive_path from task_attachment a"
              + " join task t on t.id=a.task_id where t.trashed_at is null and a.project_id"
              + VISIBLE
              + " order by a.id",
          id);
      jdbc.query(
          con -> {
            var ps =
                con.prepareStatement(
                    "select a.id,a.storage_key from task_attachment a join task t on t.id=a.task_id"
                        + " where a.storage_key is not null and t.trashed_at is null and"
                        + " a.project_id"
                        + VISIBLE
                        + " order by a.id");
            ps.setLong(1, id);
            ps.setFetchSize(100);
            return ps;
          },
          (RowCallbackHandler)
              row -> {
                String key = row.getString(2);
                if (!key.matches("[0-9a-fA-F-]{36}\\.[a-zA-Z0-9]{1,8}"))
                  throw ApiException.conflict(
                      "An attachment has an invalid storage reference. Contact the operator.");
                try (var input =
                    Files.newInputStream(
                        Path.of("data", "attachments").toAbsolutePath().resolve(key),
                        LinkOption.NOFOLLOW_LINKS)) {
                  archive.file("files/" + row.getLong(1), input);
                } catch (IOException error) {
                  throw ApiException.conflict(
                      "An attachment is unavailable. Retry the complete export after it is"
                          + " restored.");
                }
              });
      var manifest = new LinkedHashMap<String, Object>();
      manifest.put("schemaVersion", 1);
      manifest.put("createdAt", Instant.now());
      manifest.put("accountId", id);
      manifest.put("sections", archive.sections);
      manifest.put(
          "scope",
          "Account/preferences and your attributed contributions; collaborative content only in"
              + " current readable, non-deleted projects and live tasks. Attachment links are data"
              + " only; stored files are included. Other participants' unrevealed poker votes,"
              + " credential hashes, session/reset/unsubscribe tokens and server configuration are"
              + " excluded. Legacy tasks have no creator identity and are included only through"
              + " current project access.");
      archive.entry("manifest.json", json.writeValueAsBytes(manifest));
      byte[] bytes = archive.finish();
      audit.record(
          null,
          id,
          "Account",
          "ACCOUNT_EXPORTED",
          "account",
          id,
          Map.of("sections", archive.sections.size(), "zipBytes", bytes.length));
      return bytes;
    } catch (UncheckedIOException | IOException failure) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Could not create the account archive.");
    } finally {
      exporting.release();
    }
  }

  private final class Archive implements AutoCloseable {
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    final ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8);
    final Map<String, Integer> sections = new LinkedHashMap<>();
    final long limit;
    long written;

    Archive(long limit) {
      this.limit = limit;
    }

    void write(byte[] data) throws IOException {
      written += data.length;
      if (written > limit)
        throw new ResponseStatusException(
            HttpStatus.PAYLOAD_TOO_LARGE,
            "Account export exceeds the configured archive limit. An operator can raise it for an"
                + " assisted complete export.");
      zip.write(data);
    }

    void entry(String name, byte[] data) throws IOException {
      zip.putNextEntry(new ZipEntry(name));
      write(data);
      zip.closeEntry();
    }

    void rows(String name, String sql, Object... args) throws IOException {
      zip.putNextEntry(new ZipEntry(name));
      write(new byte[] {'['});
      int[] count = {0};
      jdbc.query(
          con -> {
            var ps =
                con.prepareStatement(
                    "select row_to_json(export_row)::text from (" + sql + ") export_row");
            ps.setFetchSize(200);
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            return ps;
          },
          (RowCallbackHandler)
              r -> {
                if (++count[0] > 100000)
                  throw new ResponseStatusException(
                      HttpStatus.PAYLOAD_TOO_LARGE,
                      "A data section exceeds 100000 rows. Request an assisted complete export.");
                try {
                  if (count[0] > 1) write(new byte[] {','});
                  write(r.getString(1).getBytes(StandardCharsets.UTF_8));
                } catch (IOException error) {
                  throw new UncheckedIOException(error);
                }
              });
      write(new byte[] {']'});
      zip.closeEntry();
      sections.put(name, count[0]);
    }

    void file(String name, InputStream input) throws IOException {
      zip.putNextEntry(new ZipEntry(name));
      byte[] b = new byte[8192];
      int n;
      while ((n = input.read(b)) != -1) write(Arrays.copyOf(b, n));
      zip.closeEntry();
    }

    byte[] finish() throws IOException {
      zip.finish();
      return bytes.toByteArray();
    }

    public void close() throws IOException {
      zip.close();
    }
  }
}
