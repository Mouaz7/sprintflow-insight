package com.sprintflowinsight.automation;

import com.sprintflowinsight.VersionPrecondition;
import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.workspace.customfield.CustomFieldService;
import com.sprintflowinsight.workspace.customfield.FieldCondition;
import com.sprintflowinsight.workspace.customfield.FieldOperator;
import com.sprintflowinsight.workspace.project.AccessContext.Need;
import com.sprintflowinsight.workspace.project.ProjectAccessService;
import com.sprintflowinsight.workspace.project.ProjectMutationService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AutomationRules {
  private static final int MAX_RULES = 50;
  private static final String COLUMNS = "id,name,source,trigger_kind,enabled,version,updated_at";

  private final ProjectAccessService access;
  private final ProjectMutationService mutations;
  private final CustomFieldService customFields;
  private final JdbcTemplate jdbc;

  AutomationRules(
      ProjectAccessService access,
      ProjectMutationService mutations,
      CustomFieldService customFields,
      JdbcTemplate jdbc) {
    this.access = access;
    this.mutations = mutations;
    this.customFields = customFields;
    this.jdbc = jdbc;
  }

  record Draft(
      @NotBlank @Size(max = 80) String name,
      @NotBlank @Size(max = AutomationDsl.MAX_SOURCE_LENGTH) String source,
      @NotNull Boolean enabled) {}

  record Rule(
      long id,
      String name,
      String source,
      AutomationRule.TriggerKind trigger,
      boolean enabled,
      long version,
      Instant updatedAt) {}

  record Run(
      long id,
      long ruleId,
      String ruleName,
      long taskId,
      String taskKey,
      AutomationRule.TriggerKind trigger,
      String outcome,
      String detail,
      Instant executedAt) {}

  @Transactional(readOnly = true)
  public List<Rule> list(long projectId, HttpSession session) {
    access.require(projectId, session, Need.MANAGE_ARCHIVED);
    return jdbc.query(
        "select " + COLUMNS + " from automation_rule where project_id=? order by id",
        AutomationRules::readRule,
        projectId);
  }

  @Transactional(readOnly = true)
  public List<Run> recentRuns(long projectId, HttpSession session) {
    access.require(projectId, session, Need.MANAGE_ARCHIVED);
    return jdbc.query(
        """
        select r.id,r.rule_id,r.rule_name,r.task_id,p.key || '-' || t.number,
          r.trigger_kind,r.outcome,r.detail,r.executed_at
        from automation_run r join projects p on p.id=r.project_id
        left join task t on t.id=r.task_id and t.project_id=r.project_id and t.trashed_at is null
        where r.project_id=? order by r.id desc limit 100
        """,
        (row, index) ->
            new Run(
                row.getLong(1),
                row.getLong(2),
                row.getString(3),
                row.getLong(4),
                row.getString(5),
                AutomationRule.TriggerKind.valueOf(row.getString(6)),
                row.getString(7),
                row.getString(8),
                row.getTimestamp(9).toInstant()),
        projectId);
  }

  @Transactional
  public Rule create(long projectId, Draft draft, HttpSession session) {
    manageLock(projectId, session);
    Integer count =
        jdbc.queryForObject(
            "select count(*) from automation_rule where project_id=?", Integer.class, projectId);
    if (count != null && count >= MAX_RULES)
      throw ApiException.conflict("A project can have at most 50 automation rules.");
    AutomationRule compiled = compile(projectId, draft.source());
    try {
      return jdbc.queryForObject(
          """
          insert into automation_rule(project_id,name,source,trigger_kind,enabled) values(?,?,?,?,?)
          returning
          """
              + COLUMNS,
          AutomationRules::readRule,
          projectId,
          draft.name().strip(),
          draft.source(),
          compiled.trigger().kind().name(),
          draft.enabled());
    } catch (DuplicateKeyException e) {
      throw ApiException.conflict("Use a different automation rule name.");
    }
  }

  @Transactional
  public Rule replace(long projectId, long id, Draft draft, String ifMatch, HttpSession session) {
    manageLock(projectId, session);
    Rule previous = find(projectId, id).orElseThrow(AutomationRules::missing);
    VersionPrecondition.require(ifMatch, previous.version());
    AutomationRule compiled = compile(projectId, draft.source());
    try {
      return jdbc.queryForObject(
          """
update automation_rule set name=?,source=?,trigger_kind=?,enabled=?,version=version+1,updated_at=now()
where project_id=? and id=? returning
"""
              + COLUMNS,
          AutomationRules::readRule,
          draft.name().strip(),
          draft.source(),
          compiled.trigger().kind().name(),
          draft.enabled(),
          projectId,
          id);
    } catch (DuplicateKeyException e) {
      throw ApiException.conflict("Use a different automation rule name.");
    }
  }

  @Transactional
  public void delete(long projectId, long id, String ifMatch, HttpSession session) {
    manageLock(projectId, session);
    Rule previous = find(projectId, id).orElseThrow(AutomationRules::missing);
    VersionPrecondition.require(ifMatch, previous.version());
    jdbc.update("delete from automation_rule where project_id=? and id=?", projectId, id);
  }

  void manageLock(long projectId, HttpSession session) {
    access.adminOrOwner(projectId, session);
    mutations.lock(projectId);
    access.forget(projectId);
    access.adminOrOwner(projectId, session);
  }

  List<Rule> enabled(long projectId, AutomationRule.TriggerKind kind) {
    return jdbc.query(
        "select "
            + COLUMNS
            + " from automation_rule"
            + " where project_id=? and trigger_kind=? and enabled order by id",
        AutomationRules::readRule,
        projectId,
        kind.name());
  }

  Optional<Rule> find(long projectId, long id) {
    return jdbc
        .query(
            "select " + COLUMNS + " from automation_rule where project_id=? and id=?",
            AutomationRules::readRule,
            projectId,
            id)
        .stream()
        .findFirst();
  }

  Optional<String> assignee(long projectId, String email) {
    return jdbc
        .queryForList(
            """
            select email from project_member where project_id=? and lower(email)=?
            """,
            String.class,
            projectId,
            email.strip().toLowerCase(Locale.ROOT))
        .stream()
        .findFirst();
  }

  private AutomationRule compile(long projectId, String source) {
    AutomationRule rule;
    try {
      rule = AutomationDsl.parse(source);
    } catch (AutomationDsl.ParseException error) {
      throw ApiException.badRequest("Line " + error.line() + ": " + error.getMessage());
    }
    for (AutomationRule.Condition condition : rule.conditions()) {
      if (condition instanceof AutomationRule.CustomField field)
        customFields.prepareForProject(projectId, customCondition(field));
    }
    if (rule.action().kind() == AutomationRule.ActionKind.ASSIGN
        && assignee(projectId, rule.action().value()).isEmpty())
      throw ApiException.badRequest("Assign this task to a member of the same project.");
    return rule;
  }

  static FieldCondition customCondition(AutomationRule.CustomField field) {
    return new FieldCondition(
        field.fieldId(), FieldOperator.valueOf(field.comparison().name()), field.operand());
  }

  private static Rule readRule(ResultSet row, int index) throws SQLException {
    return new Rule(
        row.getLong(1),
        row.getString(2),
        row.getString(3),
        AutomationRule.TriggerKind.valueOf(row.getString(4)),
        row.getBoolean(5),
        row.getLong(6),
        row.getTimestamp(7).toInstant());
  }

  private static ApiException missing() {
    return ApiException.notFound("Automation rule not found in this project.");
  }
}
