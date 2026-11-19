package com.sprintflowinsight.workspace.customfield;

import static org.assertj.core.api.Assertions.*;

import com.sprintflowinsight.common.error.ApiException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class FieldDefinitionRequestTest {
  @Test
  void normalizesWhitespaceAndAcceptsExactLengthAndOptionLimits() {
    var request =
        new FieldDefinitionRequest(" Name ", FieldKind.SELECT, List.of(" Ready ", "Waiting"));
    assertThat(request.normalizedName()).isEqualTo("Name");
    assertThat(request.normalizedOptions()).containsExactly("Ready", "Waiting");
    assertThat(new FieldDefinitionRequest("x".repeat(80), FieldKind.TEXT, null).normalizedName())
        .hasSize(80);
    assertThat(new FieldDefinitionRequest("Name", FieldKind.TEXT, null).normalizedOptions())
        .isEmpty();
    assertThat(
            new FieldDefinitionRequest(
                    "Name",
                    FieldKind.SELECT,
                    java.util.stream.IntStream.range(0, 30).mapToObj(n -> "Option " + n).toList())
                .normalizedOptions())
        .hasSize(30);
    assertThat(
            new FieldDefinitionRequest("Name", FieldKind.SELECT, List.of("x".repeat(80)))
                .normalizedOptions()
                .getFirst())
        .hasSize(80);
  }

  @Test
  void rejectsNullBlankLongDuplicateAndWrongTypeDefinitions() {
    for (String name : Arrays.asList(null, "", "  ", "x".repeat(81), "a\0b"))
      assertThatThrownBy(
              () -> new FieldDefinitionRequest(name, FieldKind.TEXT, List.of()).normalizedName())
          .isInstanceOf(ApiException.class);
    for (var options :
        List.of(
            List.<String>of(),
            List.of(" "),
            List.of("x".repeat(81)),
            List.of("a\0b"),
            Arrays.asList((String) null),
            List.of(" Ready", "Ready "),
            java.util.stream.IntStream.range(0, 31).mapToObj(n -> "Option " + n).toList()))
      assertThatThrownBy(
              () ->
                  new FieldDefinitionRequest("Name", FieldKind.SELECT, options).normalizedOptions())
          .isInstanceOf(ApiException.class);
    assertThatThrownBy(
            () -> new FieldDefinitionRequest("Name", FieldKind.SELECT, null).normalizedOptions())
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(
            () -> new FieldDefinitionRequest("Name", null, List.of()).normalizedOptions())
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(
            () ->
                new FieldDefinitionRequest("Name", FieldKind.NUMBER, List.of("No"))
                    .normalizedOptions())
        .isInstanceOf(ApiException.class);
  }
}
