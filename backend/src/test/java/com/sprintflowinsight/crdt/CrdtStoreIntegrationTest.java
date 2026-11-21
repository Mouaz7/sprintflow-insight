package com.sprintflowinsight.crdt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.sprintflowinsight.support.ApiIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class CrdtStoreIntegrationTest extends ApiIntegrationTest {
  @Autowired CrdtStore store;
  @Autowired JdbcTemplate jdbc;
  @Autowired TransactionTemplate transactions;

  @Test
  void compressedSnapshotAndReceiptsSurviveCompactionAndPreventReplayGrowth() {
    long project = project(user(), null).getId();
    long otherProject = project(user(), null).getId();
    long task =
        jdbc.queryForObject(
            """
            insert into task(project_id,title,status,is_blocked,number,story_points,version)
            values(?,'Collaborative task','To Do',false,1,0,0) returning id
            """,
            Long.class,
            project);
    UUID firstUpdate = UUID.randomUUID();
    transactions.executeWithoutResult(
        status -> {
          var doc = store.open(project, CrdtTarget.TASK, task, "Initial content");
          assertThat(doc.revision()).isZero();
          assertThat(store.find(otherProject, CrdtTarget.TASK, task, false)).isEmpty();
          for (int n = 1; n <= 130; n++) {
            var current = store.find(project, CrdtTarget.TASK, task, true).orElseThrow();
            var result =
                store.append(
                    current, n == 1 ? firstUpdate : UUID.randomUUID(), new byte[] {(byte) n});
            assertThat(result.revision()).isEqualTo(n);
            assertThat(result.duplicate()).isFalse();
          }
          var snapshot = store.find(project, CrdtTarget.TASK, task, true).orElseThrow();
          assertThat(snapshot.snapshotRevision()).isEqualTo(128);
          assertThat(CrdtFrames.unpack(snapshot.snapshot())).hasSize(128);
          assertThat(CrdtFrames.unpack(snapshot.snapshot()).getFirst()).containsExactly(1);
          assertThat(store.after(snapshot.id(), snapshot.snapshotRevision()))
              .extracting(CrdtStore.Update::revision)
              .containsExactly(129L, 130L);
          assertThat(store.append(snapshot, firstUpdate, new byte[] {1}))
              .isEqualTo(new CrdtStore.Appended(1, true));
          assertThatIllegalArgumentException()
              .isThrownBy(() -> store.append(snapshot, firstUpdate, new byte[] {2}));
          assertThat(store.find(project, CrdtTarget.TASK, task, false).orElseThrow().revision())
              .isEqualTo(130);
        });
    jdbc.update("delete from task where id=?", task);
    assertThat(store.find(project, CrdtTarget.TASK, task, false)).isEmpty();
  }
}
