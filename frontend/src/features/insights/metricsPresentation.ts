import type { Metrics } from '../../api/contracts';
export function metricsPresentation(
  metrics: Metrics,
  language: 'EN' | 'SV',
  sprintId: string,
) {
  const sv = language === 'SV';
  const cards: [string, string, string][] = [
    [
      sv ? 'Cykeltid' : 'Cycle time',
      metrics.cycleTimeHours == null ? '—' : `${metrics.cycleTimeHours} h`,
      sv ? 'Aktivt arbete till klart' : 'Active work to completion',
    ],
    [
      sv ? 'Ledtid' : 'Lead time',
      metrics.leadTimeHours == null ? '—' : `${metrics.leadTimeHours} h`,
      sv ? 'Skapad till klart' : 'Creation to completion',
    ],
    [
      sv ? 'Genomströmning' : 'Throughput',
      String(metrics.throughput ?? metrics.throughputLast14Days ?? 0),
      sprintId
        ? sv
          ? 'Avslutade i vald sprint'
          : 'Completed in the selected sprint'
        : sv
          ? 'Klart senaste 14 dagarna'
          : 'Completed in the last 14 days',
    ],
    [
      sv ? 'Hastighet' : 'Velocity',
      String(metrics.velocityStoryPoints ?? 0),
      sprintId
        ? sv
          ? 'Poäng klara i vald sprint'
          : 'Points completed in the selected sprint'
        : sv
          ? 'Slutförda poäng'
          : 'Completed story points',
    ],
    [
      sv ? 'Slutfört' : 'Completion',
      `${metrics.completionRate ?? 0}%`,
      sv ? 'Andel klara uppgifter' : 'Share of completed tasks',
    ],
    [
      sv ? 'Försenade' : 'Overdue',
      String(metrics.overdueTasks ?? 0),
      sv ? 'Öppna uppgifter efter slutdatum' : 'Open tasks past their due date',
    ],
  ];
  const burndown = Array.isArray(metrics.burndown)
    ? (metrics.burndown as { date: string; remainingPoints: number }[])
    : [];
  const burndownReady =
    metrics.burndownAvailable !== false &&
    burndown.length >= 2 &&
    burndown.some((day) => day.remainingPoints > 0);
  const allTasksComplete =
    Number(metrics.completedTasks ?? 0) > 0 &&
    Number(metrics.completedTasks) === Number(metrics.taskCount);
  const burndownEmptyTitle =
    Number(metrics.taskCount ?? 0) === 0
      ? sv
        ? 'Lägg till uppgifter för att följa arbetet'
        : 'Add tasks to start tracking delivery'
      : metrics.burndownAvailable === false
        ? sv
          ? 'Historiken är inte komplett ännu'
          : 'History is not complete yet'
        : burndown.length < 2
          ? sv
            ? 'För lite historik för en graf'
            : 'Not enough history for a chart'
          : allTasksComplete
            ? sv
              ? 'Allt arbete är klart'
              : 'All work is complete'
            : sv
              ? 'Inga återstående poäng i perioden'
              : 'No remaining points in this period';
  const burndownEmptyDetail =
    Number(metrics.taskCount ?? 0) === 0
      ? sv
        ? 'Skapa uppgifter och ange story points så visas leveranshistoriken här.'
        : 'Create tasks and add story points to see delivery history here.'
      : metrics.burndownAvailable === false
        ? sv
          ? 'Alla uppgifter behöver statushistorik innan vi kan visa ett tillförlitligt diagram.'
          : 'Every task needs status history before we can show a trustworthy chart.'
        : burndown.length < 2
          ? sv
            ? 'Diagrammet visas när det finns minst två dagar med sparad aktivitet.'
            : 'The chart appears when at least two days of saved activity are available.'
          : sv
            ? 'Det finns ingen meningsfull trend att visa för den valda perioden.'
            : 'There is no meaningful trend to plot for the selected period.';
  const statusCounts = (metrics.statusCounts ?? {}) as Record<string, number>;
  const health = (metrics.sprintHealth ?? {}) as {
    status?: string;
    explanation?: string;
    blockedTasks?: number;
    workInProgress?: number;
  };
  const sprintPace = metrics.sprintPace as
    | {
        available?: boolean;
        status?: string;
        plannedStoryPoints?: number;
        deliveredStoryPoints?: number;
        expectedStoryPoints?: number;
        elapsedPercent?: number;
      }
    | undefined;
  const businessValue = metrics.businessValue as
    | {
        available?: boolean;
        expectedValue?: number;
        deliveredValue?: number;
        deliveredPercent?: number;
        scoredStories?: number;
      }
    | undefined;
  const completedSprints = Array.isArray(metrics.completedSprints)
    ? (metrics.completedSprints as {
        id: number;
        name: string;
        completedAt?: string;
        plannedStoryPoints: number;
        actualStoryPoints: number;
        completedTasks: number;
      }[])
    : [];
  const chartLeft = 52;
  const chartRight = 704;
  const chartTop = 22;
  const chartBottom = 194;
  const chartMax = Math.max(
    10,
    Math.ceil(Math.max(0, ...burndown.map((day) => day.remainingPoints)) / 10) *
      10,
  );
  const chartCoords = burndown.map((day, index) => ({
    ...day,
    x:
      chartLeft +
      (index * (chartRight - chartLeft)) / Math.max(1, burndown.length - 1),
    y:
      chartBottom - (day.remainingPoints / chartMax) * (chartBottom - chartTop),
  }));
  const chartPath = chartCoords
    .map((point) => `${point.x},${point.y}`)
    .join(' ');
  const dateLabelIndexes = [
    ...new Set([
      0,
      Math.floor((burndown.length - 1) * 0.25),
      Math.floor((burndown.length - 1) * 0.5),
      Math.floor((burndown.length - 1) * 0.75),
      burndown.length - 1,
    ]),
  ].filter((index) => index >= 0);
  const chartGridValues = [0, chartMax / 2, chartMax];
  const query = sprintId ? `?sprintId=${sprintId}` : '';
  return {
    metrics,
    sv,
    sprintId,
    language,
    cards,
    burndown,
    burndownReady,
    allTasksComplete,
    burndownEmptyTitle,
    burndownEmptyDetail,
    statusCounts,
    health,
    sprintPace,
    businessValue,
    completedSprints,
    chartLeft,
    chartRight,
    chartTop,
    chartBottom,
    chartMax,
    chartCoords,
    chartPath,
    dateLabelIndexes,
    chartGridValues,
    query,
  };
}
export type MetricsPresentation = ReturnType<typeof metricsPresentation>;
