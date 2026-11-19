package com.sprintflowinsight.workspace.customfield;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprintflowinsight.support.MigrationFixture;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class CustomFieldMigrationTest {
  @Test
  void upgradesV21WithoutChangingExistingRevisionsAndEnforcesTypedProjectMembership()
      throws Exception {
    try (var db = new MigrationFixture();
        var sql = db.connection().createStatement()) {
      db.migrateTo("21");
      sql.executeUpdate(
          "insert into users(id,email) values (1,'one@example.com'),(2,'two@example.com')");
      sql.executeUpdate(
          "insert into projects(id,name,owner_email,version,key) values"
              + " (1,'One','one@example.com',4,'AA'),(2,'Two','two@example.com',0,'BB')");
      sql.executeUpdate(
          "insert into project_member(id,project_id,user_id,email,role) values"
              + " (111,1,1,'one@example.com','Owner'),(222,2,2,'two@example.com','Owner')");
      sql.executeUpdate(
          "insert into task(id,project_id,number,title,status,is_blocked,story_points,version)"
              + " values (11,1,1,'Existing','To Do',false,3,7),(22,2,1,'Foreign','To"
              + " Do',false,1,0)");
      sql.executeUpdate(
          "insert into"
              + " task_status_history(task_id,project_id,status,changed_at,actor_email,story_points)"
              + " values (11,1,'To Do','2026-01-01T00:00:00Z','one@example.com',3)");
      assertThat(db.migrateTo("22").migrationsExecuted).isEqualTo(1);
      // Validate the completed upgrade as later features add migrations after V22.
      db.migrateTo(null);
      db.flyway().validate();
      try (var rows =
          sql.executeQuery("select version,custom_fields_revision from task where id=11")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getLong(1)).isEqualTo(7);
        assertThat(rows.getLong(2)).isZero();
      }
      sql.executeUpdate(
          "insert into custom_field(id,project_id,name,kind) values"
              + " (1,1,'Reference','TEXT'),(2,1,'Budget','NUMBER'),(3,1,'Due','DATE'),"
              + " (5,1,'Reviewer','USER'),(6,2,'Foreign field','TEXT')");
      sql.executeUpdate(
          "insert into custom_field(id,project_id,name,kind,options)"
              + " values (4,1,'Stage','SELECT','[\"Ready\"]')");
      sql.executeUpdate(
          "insert into task_custom_value(task_id,field_id,project_id,kind,text_value)"
              + " values (11,1,1,'TEXT','Saved reference'),(11,4,1,'SELECT','Ready')");
      sql.executeUpdate(
          "insert into task_custom_value(task_id,field_id,project_id,kind,number_value)"
              + " values (11,2,1,'NUMBER',9007199254740993.1)");
      sql.executeUpdate(
          "insert into task_custom_value(task_id,field_id,project_id,kind,date_value)"
              + " values (11,3,1,'DATE','2026-11-18')");
      sql.executeUpdate(
          "insert into task_custom_value(task_id,field_id,project_id,kind,member_id)"
              + " values (11,5,1,'USER',111)");
      for (String invalid :
          new String[] {
            "insert into task_custom_value(task_id,field_id,project_id,kind,text_value) values"
                + " (22,1,1,'TEXT','Cross task')",
            "insert into task_custom_value(task_id,field_id,project_id,kind,text_value) values"
                + " (11,6,1,'TEXT','Cross field')",
            "update task_custom_value set member_id=222 where task_id=11 and field_id=5",
            "update task_custom_value set kind='NUMBER',text_value=null,number_value=1 where"
                + " task_id=11 and field_id=1",
            "update task_custom_value set number_value=1 where task_id=11 and field_id=1",
            "update task_custom_value set date_value='infinity' where task_id=11 and field_id=3",
            "update task_custom_value set number_value='NaN' where task_id=11 and field_id=2",
            "insert into custom_field(project_id,name,kind) values (1,'reference','TEXT')",
            "insert into custom_field(project_id,name,kind,options) values (1,'Invalid"
                + " options','SELECT','{}')"
          }) {
        assertThatThrownBy(() -> sql.executeUpdate(invalid))
            .as(invalid)
            .isInstanceOf(SQLException.class);
      }
      try (var rows =
          sql.executeQuery(
              "select number_value from task_custom_value where task_id=11 and field_id=2")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getBigDecimal(1)).isEqualByComparingTo("9007199254740993.1");
      }
      sql.executeUpdate("delete from project_member where id=111");
      sql.executeUpdate(
          "insert into project_member(id,project_id,user_id,email,role)"
              + " values (333,1,1,'one@example.com','Owner')");
      try (var rows = sql.executeQuery("select count(*) from task_custom_value where task_id=11")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getInt(1)).isEqualTo(4);
      }
      try (var rows =
          sql.executeQuery(
              "select status,story_points from task_status_history where task_id=11")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getString(1)).isEqualTo("To Do");
        assertThat(rows.getInt(2)).isEqualTo(3);
      }
      sql.executeUpdate("delete from custom_field where id=1");
      try (var rows = sql.executeQuery("select count(*) from task_custom_value where field_id=1")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getInt(1)).isZero();
      }
      sql.executeUpdate("delete from task where id=11");
      try (var rows = sql.executeQuery("select count(*) from task_custom_value where task_id=11")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getInt(1)).isZero();
      }
    }
  }
}
