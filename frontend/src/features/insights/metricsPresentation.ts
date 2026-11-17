import type { Language, Metrics } from '../../api/contracts';
import { translator } from '../../shared/i18n/lexicon';
import { insightsText } from './i18n';
export function metricsPresentation(
  metrics: Metrics,
  language: Language,
  sprintId: string,
) {
  const t = translator(insightsText, language);
  const cards: [string, string, string][] = [
    [
      t('cycleTime'),
      metrics.cycleTimeHours == null ? '—' : `${metrics.cycleTimeHours} h`,
      t('cycleTimeDetail'),
    ],
    [
      t('leadTime'),
      metrics.leadTimeHours == null ? '—' : `${metrics.leadTimeHours} h`,
      t('leadTimeDetail'),
    ],
    [
      t('throughput'),
      String(metrics.throughput ?? metrics.throughputLast14Days ?? 0),
      t(sprintId ? 'throughputSprint' : 'throughput14'),
    ],
    [
      t('velocity'),
      String(metrics.velocityStoryPoints ?? 0),
      t(sprintId ? 'velocitySprint' : 'velocityAll'),
    ],
    [t('completion'), `${metrics.completionRate ?? 0}%`, t('completionDetail')],
    [t('overdue'), String(metrics.overdueTasks ?? 0), t('overdueDetail')],
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
  const burndownEmptyTitle = t(
    Number(metrics.taskCount ?? 0) === 0
      ? 'burndownNoTasks'
      : metrics.burndownAvailable === false
        ? 'burndownIncomplete'
        : burndown.length < 2
          ? 'burndownShort'
          : allTasksComplete
            ? 'allWorkComplete'
            : 'burndownNoRemaining',
  );
  const burndownEmptyDetail = t(
    Number(metrics.taskCount ?? 0) === 0
      ? 'burndownNoTasksDetail'
      : metrics.burndownAvailable === false
        ? 'burndownIncompleteDetail'
        : burndown.length < 2
          ? 'burndownShortDetail'
          : 'burndownFlatDetail',
  );
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
    sprintId,
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
