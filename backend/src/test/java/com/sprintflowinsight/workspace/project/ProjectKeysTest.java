package com.sprintflowinsight.workspace.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ProjectKeysTest {
  @Test
  void derivesInitialsOrTheStartOfASingleWord() {
    assertThat(ProjectKeys.derive("Sprint Flow")).isEqualTo("SF");
    assertThat(ProjectKeys.derive("sprintflow-insight web app v2")).isEqualTo("SIWA");
    assertThat(ProjectKeys.derive("Apollo")).isEqualTo("APOL");
    assertThat(ProjectKeys.derive("Go")).isEqualTo("GO");
    assertThat(ProjectKeys.derive("Åre Östra")).isEqualTo("AO");
    assertThat(ProjectKeys.derive("Ærø Ørsted")).isEqualTo("AO");
    assertThat(ProjectKeys.derive("Café")).isEqualTo("CAFE");
  }

  @Test
  void fallsBackWhenANameHasTooFewLetters() {
    for (String name : new String[] {null, "", "   ", "2026", "A", "🚀 7", "Ω"})
      assertThat(ProjectKeys.derive(name)).as(String.valueOf(name)).isEqualTo("PRJ");
  }

  @Test
  void resolvesCollisionsWithLetterSuffixes() {
    assertThat(ProjectKeys.unique("SF", key -> false)).isEqualTo("SF");
    assertThat(ProjectKeys.unique("SF", Set.of("SF")::contains)).isEqualTo("SFA");
    assertThat(ProjectKeys.unique("SF", Set.of("SF", "SFA", "SFB")::contains)).isEqualTo("SFC");
    assertThat(ProjectKeys.unique("ABCDEFGHIJ", Set.of("ABCDEFGHIJ")::contains))
        .isEqualTo("ABCDEFGHIA");
    assertThatThrownBy(() -> ProjectKeys.unique("s", key -> false))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void suffixIsBijectiveBase26() {
    assertThat(ProjectKeys.suffix(1)).isEqualTo("A");
    assertThat(ProjectKeys.suffix(26)).isEqualTo("Z");
    assertThat(ProjectKeys.suffix(27)).isEqualTo("AA");
    assertThat(ProjectKeys.suffix(52)).isEqualTo("AZ");
    assertThat(ProjectKeys.suffix(53)).isEqualTo("BA");
    assertThat(ProjectKeys.suffix(702)).isEqualTo("ZZ");
    assertThat(ProjectKeys.suffix(703)).isEqualTo("AAA");
    assertThatThrownBy(() -> ProjectKeys.suffix(0)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void validatesAndNormalizesClientKeys() {
    assertThat(ProjectKeys.isValid("SF")).isTrue();
    assertThat(ProjectKeys.isValid("ABCDEFGHIJ")).isTrue();
    for (String bad : new String[] {null, "", "S", "sf", "S1", "ABCDEFGHIJK", "S-F", "ÅÄ"})
      assertThat(ProjectKeys.isValid(bad)).as(String.valueOf(bad)).isFalse();
    assertThat(ProjectKeys.normalize(" sf ")).isEqualTo("SF");
    assertThat(ProjectKeys.normalize(null)).isNull();
    assertThat(new ProjectRequest("Name", null, " ab ").normalizedKey()).isEqualTo("AB");
    assertThat(new ProjectRequest("Name", null).normalizedKey()).isNull();
    assertThat(new ProjectRequest("Name", null, " ").normalizedKey()).isNull();
    assertThat(new TemplateRequest("Name", null, "Blank", "EN", "xy").normalizedKey())
        .isEqualTo("XY");
    assertThat(new TemplateRequest("Name", null, "Blank", "EN").normalizedKey()).isNull();
  }
}
