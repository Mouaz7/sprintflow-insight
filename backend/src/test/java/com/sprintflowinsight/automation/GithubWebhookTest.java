package com.sprintflowinsight.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprintflowinsight.common.error.ApiException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class GithubWebhookTest {
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void findsTaskKeysInAPullRequestTitleAndBranch() {
    var event =
        GithubWebhook.pullRequest(
            json.readTree(
                """
                {"action":"opened","number":37,"pull_request":{"id":600,
                 "title":"Improve SF-123 and XX-7","head":{"ref":"feature/sf-156"}}}
                """));
    assertThat(event.action()).isEqualTo("opened");
    assertThat(event.pullRequestId()).isEqualTo(600);
    assertThat(event.references())
        .singleElement()
        .satisfies(
            ref -> {
              assertThat(ref.state()).isEqualTo("OPEN");
              assertThat(ref.key()).isEqualTo("37");
              assertThat(GithubAutomation.taskNumbers(ref.text(), "SF")).containsExactly(123, 156);
            });
  }

  @Test
  void preservesOnlyTheFirstCommitSummaryAndUsesAllCommitTextForTaskReferences() {
    var event =
        GithubWebhook.push(
            json.readTree(
                """
{"ref":"refs/heads/work/SF-5","commits":[
 {"id":"AAAABBBBCCCCDDDDEEEEFFFF0000111122223333","message":"Short summary\\nWork on SF-6"}]}
"""));
    assertThat(event.references())
        .singleElement()
        .satisfies(
            ref -> {
              assertThat(ref.kind()).isEqualTo("COMMIT");
              assertThat(ref.key()).isEqualTo("aaaabbbbccccddddeeeeffff0000111122223333");
              assertThat(ref.title()).isEqualTo("Short summary");
              assertThat(GithubAutomation.taskNumbers(ref.text(), "SF")).containsExactly(5, 6);
            });
  }

  @Test
  void requiresCanonicalIdentifiersAndIgnoresTagPushes() {
    assertThatThrownBy(
            () ->
                GithubWebhook.push(
                    json.readTree(
                        """
                        {"ref":"refs/heads/main","commits":[{"id":"other","message":"SF-123"}]}
                        """)))
        .isInstanceOf(ApiException.class);
    assertThat(
            GithubWebhook.push(
                    json.readTree(
                        """
                        {"ref":"refs/tags/v1.0.0","commits":[]}
                        """))
                .references())
        .isEmpty();
  }
}
