package com.sprintflowinsight.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprintflowinsight.support.MigrationFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/** The rotation job's SQL (batching, SKIP LOCKED, prefix filter) on real PostgreSQL. */
class ReEncryptionIntegrationTest {
  @Test
  void rotatesAWholeTableToTheCurrentKey() throws Exception {
    byte[] oldKey = EncryptionServiceTest.key();
    byte[] newKey = EncryptionServiceTest.key();
    EncryptionService before = new EncryptionService(Keyring.of(oldKey));
    EncryptionService after = new EncryptionService(Keyring.of(newKey, oldKey));
    try (var db = new MigrationFixture()) {
      var source = db.dataSource();
      JdbcTemplate jdbc = new JdbcTemplate(source);
      jdbc.execute("create table secret_store(id bigint primary key, secret text)");
      for (long id = 1; id <= 25; id++)
        jdbc.update(
            "insert into secret_store(id, secret) values (?, ?)",
            id,
            id == 13 ? null : before.encrypt("value-" + id, "secret_store.secret"));
      StaticListableBeanFactory beans = new StaticListableBeanFactory();
      beans.addBean("secrets", EncryptedColumn.of("secret_store", "id", "secret"));
      ReEncryptionJob job =
          new ReEncryptionJob(
              after,
              jdbc,
              new DataSourceTransactionManager(source),
              beans.getBeanProvider(EncryptedColumn.class),
              4);

      assertThat(job.pending()).isEqualTo(24);
      assertThat(job.run()).isEqualTo(new ReEncryptionJob.Report(24, 0));
      assertThat(job.pending()).isZero();

      EncryptionService retiredRemoved = new EncryptionService(Keyring.of(newKey));
      jdbc.query(
          "select id, secret from secret_store where secret is not null",
          row -> {
            assertThat(retiredRemoved.decrypt(row.getString(2), "secret_store.secret"))
                .isEqualTo("value-" + row.getLong(1));
          });
      assertThat(job.run()).isEqualTo(new ReEncryptionJob.Report(0, 0));
    }
  }
}
