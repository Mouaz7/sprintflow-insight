package com.sprintflowinsight.workspace.organization;

import com.sprintflowinsight.auth.SessionUser;
import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.workspace.events.OrganizationMfaChanged;
import com.sprintflowinsight.workspace.organization.OrgAccessService.Need;
import com.sprintflowinsight.workspace.project.ProjectRole;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/organizations/{slug}/security")
class OrganizationMfaController {
  record Policy(@NotNull Boolean requireTwoFactor) {}

  private final OrgAccessService access;
  private final SessionUser account;
  private final JdbcTemplate jdbc;
  private final ApplicationEventPublisher events;

  OrganizationMfaController(
      OrgAccessService access,
      SessionUser account,
      JdbcTemplate jdbc,
      ApplicationEventPublisher events) {
    this.access = access;
    this.account = account;
    this.jdbc = jdbc;
    this.events = events;
  }

  @GetMapping
  @Transactional(readOnly = true)
  public Policy read(@PathVariable String slug, HttpSession session) {
    long id = access.require(slug, session, Need.MANAGE).organizationId();
    return new Policy(
        jdbc.queryForObject("select require_mfa from organization where id=?", Boolean.class, id));
  }

  @PutMapping
  @Transactional
  public Policy update(
      @PathVariable String slug, @Valid @RequestBody Policy policy, HttpSession session) {
    var organization = access.require(slug, session, Need.MANAGE);
    if (organization.role() != ProjectRole.OWNER)
      throw ApiException.forbidden("Only the organization owner can change sign-in requirements.");
    if (policy.requireTwoFactor() && !account.mfaVerified())
      throw ApiException.conflict("Enable two-factor authentication for your account first.");
    if (jdbc.update(
            "update organization set require_mfa=? where id=? and require_mfa<>?",
            policy.requireTwoFactor(),
            organization.organizationId(),
            policy.requireTwoFactor())
        != 0)
      events.publishEvent(
          new OrganizationMfaChanged(organization.organizationId(), policy.requireTwoFactor()));
    return policy;
  }
}
