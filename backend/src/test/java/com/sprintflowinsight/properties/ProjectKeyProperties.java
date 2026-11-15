package com.sprintflowinsight.properties;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprintflowinsight.workspace.project.ProjectKeys;
import com.sprintflowinsight.workspace.task.TaskKey;
import java.util.HashSet;
import java.util.Set;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;
import net.jqwik.api.constraints.StringLength;
import net.jqwik.api.constraints.UpperChars;

/** Project and task keys: always valid, never duplicated, and parsing inverts formatting. */
class ProjectKeyProperties {
  @Property(tries = 500)
  void derivedKeysAreAlwaysValid(@ForAll String name) {
    assertThat(ProjectKeys.isValid(ProjectKeys.derive(name))).isTrue();
  }

  @Property(tries = 200)
  void allocatingKeysForManyProjectsNeverRepeatsOne(
      @ForAll @Size(max = 60) java.util.List<@StringLength(max = 12) String> names) {
    Set<String> taken = new HashSet<>();
    for (String name : names) {
      String key = ProjectKeys.unique(ProjectKeys.derive(name), taken::contains);
      assertThat(ProjectKeys.isValid(key)).isTrue();
      assertThat(taken.add(key)).as("fresh key %s", key).isTrue();
    }
  }

  @Property(tries = 200)
  void collisionSuffixesStayWithinTenLetters(
      @ForAll @UpperChars @StringLength(min = 2, max = 10) String base,
      @ForAll @IntRange(min = 0, max = 800) int alreadyTaken) {
    Set<String> taken = new HashSet<>();
    for (int i = 0; i < alreadyTaken; i++)
      taken.add(ProjectKeys.unique(base, taken::contains));
    String next = ProjectKeys.unique(base, taken::contains);
    assertThat(ProjectKeys.isValid(next)).isTrue();
    assertThat(taken).doesNotContain(next);
  }

  @Property(tries = 300)
  void taskKeysRoundTrip(
      @ForAll @UpperChars @StringLength(min = 2, max = 10) String project,
      @ForAll @IntRange(min = 1, max = 999_999_999) int number) {
    TaskKey key = new TaskKey(project, number);
    assertThat(TaskKey.parse(key.toString())).contains(key);
    assertThat(TaskKey.parse(key.toString().toLowerCase(java.util.Locale.ROOT))).contains(key);
  }

  @Property(tries = 500)
  void parsingArbitraryTextNeverThrows(@ForAll String text) {
    TaskKey.parse(text).ifPresent(key -> assertThat(key.number()).isPositive());
  }
}
