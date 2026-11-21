package com.sprintflowinsight.workspace.organization;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprintflowinsight.support.MigrationFixture;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class OrganizationMigrationTest {
  @Test
  void assignsPreviouslyCreatedProjectsAndNewAccountsToPersonalOrganizations() throws Exception {
    try (var fixture = new MigrationFixture()) {
      fixture.migrateTo("31");
      var jdbc = new JdbcTemplate(fixture.dataSource());
      jdbc.update(
          "insert into users(id,email,display_name) values (10,'legacy-owner@example.com','Legacy"
              + " owner')");
      jdbc.update(
          """
          insert into projects(id,name,owner_email,key,version) values
          (10,'Existing project','legacy-owner@example.com','EXIST',0),
          (11,'Unlinked project','legacy-missing@example.com','UNL',0)
          """);
      assertThat(fixture.migrateTo("32").migrationsExecuted).isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  """
select o.slug from projects p join organization o on o.id=p.organization_id where p.id=10
""",
                  String.class))
          .isEqualTo("personal-10");
      assertThat(
              jdbc.queryForObject(
                  """
select o.slug from projects p join organization o on o.id=p.organization_id where p.id=11
""",
                  String.class))
          .isEqualTo("legacy-unassigned");
      assertThat(
              jdbc.queryForObject(
                  """
select m.role from organization_member m join organization o on o.id=m.organization_id
where o.slug='personal-10' and m.user_id=10
""",
                  String.class))
          .isEqualTo("Owner");

      jdbc.update("insert into users(id,email) values (20,'later-owner@example.com')");
      jdbc.update(
          """
          insert into projects(id,name,owner_email,key,version)
          values (20,'Existing project','later-owner@example.com','LATER',0)
          """);
      assertThat(
              jdbc.queryForObject(
                  """
select o.slug from projects p join organization o on o.id=p.organization_id where p.id=20
""",
                  String.class))
          .isEqualTo("personal-20");
    }
  }
}
