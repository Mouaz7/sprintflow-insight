package com.sprintflowinsight.workspace.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprintflowinsight.workspace.time.CapacityMath.Load;
import com.sprintflowinsight.workspace.time.CapacityMath.MemberCapacity;
import com.sprintflowinsight.workspace.time.CapacityMath.Variance;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CapacityMathTest {
  private static final LocalDate MONDAY = LocalDate.of(2026, 3, 2);

  @ParameterizedTest
  @CsvSource({
    "2026-03-02, 2026-03-02, 1", // Monday alone
    "2026-03-02, 2026-03-06, 5", // Monday to Friday
    "2026-03-02, 2026-03-08, 5", // a full week
    "2026-03-02, 2026-03-13, 10", // two weeks
    "2026-03-07, 2026-03-08, 0", // weekend only
    "2026-03-06, 2026-03-09, 2", // Friday to Monday
    "2026-03-08, 2026-03-14, 5", // Sunday to Saturday
    "2026-03-05, 2026-03-03, 0", // reversed
    "2024-02-26, 2024-03-01, 5", // across a leap day
  })
  void countsWeekdaysInclusive(LocalDate start, LocalDate end, int expected) {
    assertThat(CapacityMath.workingDays(start, end)).isEqualTo(expected);
  }

  @Test
  void missingDatesHaveNoWorkingDays() {
    assertThat(CapacityMath.workingDays(null, MONDAY)).isZero();
    assertThat(CapacityMath.workingDays(MONDAY, null)).isZero();
    assertThat(CapacityMath.workingDaysOff(null, MONDAY, List.of(MONDAY))).isZero();
    assertThat(CapacityMath.workingDaysOff(MONDAY, null, List.of(MONDAY))).isZero();
    assertThat(CapacityMath.workingDaysOff(MONDAY, MONDAY, null)).isZero();
  }

  @Test
  void weekendsAreNotWorkingDays() {
    assertThat(Arrays.stream(DayOfWeek.values()).filter(CapacityMath::isWorkingDay))
        .containsExactly(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY);
  }

  @Test
  void onlyDistinctWeekdaysInsideTheSprintCountAsDaysOff() {
    LocalDate friday = MONDAY.plusDays(11);
    List<LocalDate> daysOff =
        Arrays.asList(
            MONDAY, // first day, counts
            MONDAY, // duplicate
            friday, // last day, counts
            MONDAY.plusDays(5), // Saturday
            MONDAY.minusDays(3), // Friday before the sprint
            friday.plusDays(3), // Monday after the sprint
            null);
    assertThat(CapacityMath.workingDaysOff(MONDAY, friday, daysOff)).isEqualTo(2);
  }

  @Test
  void capacityScalesMinutesAndPointsByDaysOffAndAvailability() {
    MemberCapacity capacity = CapacityMath.memberCapacity(10, 1, 480, 50, 10);
    assertThat(capacity.availableDays()).isEqualTo(9);
    assertThat(capacity.availableMinutes()).isEqualTo(2160);
    assertThat(capacity.effectivePoints()).isEqualTo(4); // 4.5 rounded down

    assertThat(CapacityMath.memberCapacity(10, 0, 480, 100, 10))
        .isEqualTo(new MemberCapacity(10, 4800, 10));
    assertThat(CapacityMath.memberCapacity(10, 0, 480, 100, null).effectivePoints()).isNull();
    assertThat(CapacityMath.memberCapacity(0, 0, 480, 100, 10))
        .isEqualTo(new MemberCapacity(0, 0, 0));
    assertThat(CapacityMath.memberCapacity(10, 10, 480, 100, 10))
        .isEqualTo(new MemberCapacity(0, 0, 0));
    // Rounds down, never up: 3 days x 7 minutes x 33 % = 6.93.
    assertThat(CapacityMath.memberCapacity(3, 0, 7, 33, 1).availableMinutes()).isEqualTo(6);
  }

  @Test
  void capacityAcceptsTheBoundaryValues() {
    assertThat(CapacityMath.memberCapacity(1, 0, 1440, 100, 0).availableMinutes()).isEqualTo(1440);
    assertThat(CapacityMath.memberCapacity(1, 0, 0, 0, 0).availableMinutes()).isZero();
    assertThat(CapacityMath.memberCapacity(1, 1, 0, 0, null).availableDays()).isZero();
  }

  @Test
  void capacityRejectsImpossibleInputs() {
    assertThatThrownBy(() -> CapacityMath.memberCapacity(-1, 0, 480, 100, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> CapacityMath.memberCapacity(5, -1, 480, 100, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> CapacityMath.memberCapacity(5, 6, 480, 100, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> CapacityMath.memberCapacity(5, 0, -1, 100, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> CapacityMath.memberCapacity(5, 0, 1441, 100, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> CapacityMath.memberCapacity(5, 0, 480, -1, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> CapacityMath.memberCapacity(5, 0, 480, 101, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> CapacityMath.memberCapacity(5, 0, 480, 100, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void loadRoundsUpAndFlagsOnlyStrictOverbooking() {
    assertThat(CapacityMath.load(100, 100)).isEqualTo(new Load(100, false));
    assertThat(CapacityMath.load(101, 100)).isEqualTo(new Load(101, true));
    assertThat(CapacityMath.load(1001, 1000)).isEqualTo(new Load(101, true));
    assertThat(CapacityMath.load(1, 3)).isEqualTo(new Load(34, false));
    assertThat(CapacityMath.load(Long.MAX_VALUE, Long.MAX_VALUE)).isEqualTo(new Load(100, false));
    assertThat(CapacityMath.load(Long.MAX_VALUE, 1)).isEqualTo(new Load(Integer.MAX_VALUE, true));
    assertThat(CapacityMath.load(0, 3)).isEqualTo(new Load(0, false));
    assertThat(CapacityMath.load(0, 0)).isEqualTo(new Load(null, false));
    assertThat(CapacityMath.load(1, 0)).isEqualTo(new Load(null, true));
    assertThatThrownBy(() -> CapacityMath.load(-1, 0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> CapacityMath.load(0, -1)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void timerMinutesRoundUpWithinOneDay() {
    Instant start = Instant.parse("2026-03-02T08:00:00Z");
    assertThat(CapacityMath.timerMinutes(start, start)).isEqualTo(1);
    assertThat(CapacityMath.timerMinutes(start, start.minusSeconds(30))).isEqualTo(1);
    assertThat(CapacityMath.timerMinutes(start, start.plusSeconds(60))).isEqualTo(1);
    assertThat(CapacityMath.timerMinutes(start, start.plusMillis(60001))).isEqualTo(2);
    assertThat(CapacityMath.timerMinutes(start, start.plusSeconds(61))).isEqualTo(2);
    assertThat(CapacityMath.timerMinutes(start, start.plusSeconds(3600))).isEqualTo(60);
    assertThat(CapacityMath.timerMinutes(start, start.plusSeconds(86_400))).isEqualTo(1440);
    assertThat(CapacityMath.timerMinutes(start, start.plusSeconds(3 * 86_400))).isEqualTo(1440);
  }

  @Test
  void wholeHoursRoundDown() {
    assertThat(CapacityMath.wholeHours(0)).isZero();
    assertThat(CapacityMath.wholeHours(59)).isZero();
    assertThat(CapacityMath.wholeHours(60)).isEqualTo(1);
    assertThat(CapacityMath.wholeHours(2159)).isEqualTo(35);
    assertThat(CapacityMath.wholeHours(-5)).isZero();
    assertThat(CapacityMath.wholeHours(Long.MAX_VALUE)).isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  void varianceComparesLoggedWithEstimated() {
    assertThat(CapacityMath.variance(null, 30)).isEqualTo(new Variance(null, null));
    assertThat(CapacityMath.variance(0, 30)).isEqualTo(new Variance(30L, null));
    assertThat(CapacityMath.variance(120, 150)).isEqualTo(new Variance(30L, 25));
    assertThat(CapacityMath.variance(120, 90)).isEqualTo(new Variance(-30L, -25));
    assertThat(CapacityMath.variance(200, 201)).isEqualTo(new Variance(1L, 1)); // 0.5 rounds up
    assertThat(CapacityMath.variance(300, 301)).isEqualTo(new Variance(1L, 0));
  }
}
