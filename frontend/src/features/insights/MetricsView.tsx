import { localizeMessage } from '../../shared/i18n/messages';
import { useEffect, useState } from 'react';
import { api } from '../../api/client';
import type { Sprint, Metrics } from '../../api/contracts';

export function MetricsCards({
  projectId,
  language,
}: {
  projectId: number;
  language: 'EN' | 'SV';
}) {
  const sv = language === 'SV';
  const [metrics, setMetrics] = useState<Metrics>();
  const [sprints, setSprints] = useState<Sprint[]>([]);
  const [sprintId, setSprintId] = useState('');
  const [error, setError] = useState('');
  useEffect(() => {
    let cancelled = false;
    api<Sprint[]>(`/sprints?projectId=${projectId}`)
      .then((result) => {
        if (!cancelled) setSprints(result);
      })
      .catch((cause: Error) => {
        if (!cancelled) setError(cause.message);
      });
    return () => {
      cancelled = true;
    };
  }, [projectId]);
  useEffect(() => {
    let cancelled = false;
    setMetrics(undefined);
    setError('');
    const suffix = sprintId ? `?sprintId=${sprintId}` : '';
    api<Metrics>(`/projects/${projectId}/metrics${suffix}`)
      .then((result) => {
        if (!cancelled) setMetrics(result);
      })
      .catch((cause: Error) => {
        if (!cancelled) setError(cause.message);
      });
    return () => {
      cancelled = true;
    };
  }, [projectId, sprintId]);
  if (!metrics)
    return (
      <>
        {error && (
          <p className="form-message error" role="alert">
            {localizeMessage(error, sv ? 'SV' : 'EN')}
          </p>
        )}
        <div className="empty-state">
          {sv ? 'Laddar mätvärden…' : 'Loading metrics…'}
        </div>
      </>
    );
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
  return (
    <>
      <div className="insight-toolbar">
        <label>
          {sv ? 'Visa data' : 'Data scope'}
          <select
            value={sprintId}
            onChange={(event) => setSprintId(event.target.value)}
          >
            <option value="">{sv ? 'Hela projektet' : 'Entire project'}</option>
            {sprints.map((sprint) => (
              <option value={sprint.id} key={sprint.id}>
                {sprint.name}
              </option>
            ))}
          </select>
        </label>
        <div className="export-actions">
          <a
            className="secondary-action"
            href={`/api/projects/${projectId}/export.csv${query}`}
          >
            Export CSV
          </a>
          <a
            className="secondary-action"
            href={`/api/projects/${projectId}/export.pdf${query}`}
          >
            Export PDF
          </a>
        </div>
      </div>
      {sprintId && (
        <div className="analysis-grid">
          <article className="analysis-card">
            <div className="analysis-heading">
              <div>
                <small>{sv ? 'SPRINTTAKT' : 'SPRINT PACE'}</small>
                <h2>{sv ? 'Leverans mot plan' : 'Delivery against plan'}</h2>
              </div>
              <b
                className={
                  sprintPace?.status === 'Behind'
                    ? 'health-badge risk'
                    : 'health-badge'
                }
              >
                {sprintPace?.available
                  ? sv
                    ? sprintPace.status === 'On track'
                      ? 'I fas'
                      : 'Efter plan'
                    : sprintPace.status
                  : sv
                    ? 'Datum saknas'
                    : 'Dates needed'}
              </b>
            </div>
            <p>
              {sprintPace?.available
                ? sv
                  ? String(sprintPace.deliveredStoryPoints) +
                    ' av ' +
                    sprintPace.plannedStoryPoints +
                    ' poäng klara; ' +
                    sprintPace.expectedStoryPoints +
                    ' förväntade hittills.'
                  : String(sprintPace.deliveredStoryPoints) +
                    ' of ' +
                    sprintPace.plannedStoryPoints +
                    ' points delivered; ' +
                    sprintPace.expectedStoryPoints +
                    ' expected so far.'
                : sv
                  ? 'Lägg till start- och slutdatum på sprinten för att jämföra leveranstakt.'
                  : 'Add sprint start and end dates to compare delivery pace.'}
            </p>
            {sprintPace?.available && (
              <div className="progress-track">
                <span
                  style={{
                    width:
                      (sprintPace.plannedStoryPoints
                        ? Math.min(
                            100,
                            (sprintPace.deliveredStoryPoints! /
                              sprintPace.plannedStoryPoints) *
                              100,
                          )
                        : 0) + '%',
                  }}
                />
              </div>
            )}
          </article>
          <article className="analysis-card">
            <div className="analysis-heading">
              <div>
                <small>{sv ? 'AFFÄRSVÄRDE' : 'BUSINESS VALUE'}</small>
                <h2>{sv ? 'Levererat värde' : 'Value delivered'}</h2>
              </div>
              <b>
                {businessValue?.available
                  ? String(businessValue.deliveredPercent) + '%'
                  : '—'}
              </b>
            </div>
            <p>
              {businessValue?.available
                ? sv
                  ? String(businessValue.deliveredValue) +
                    ' av ' +
                    businessValue.expectedValue +
                    ' värdepoäng levererade från ' +
                    businessValue.scoredStories +
                    ' berättelser.'
                  : String(businessValue.deliveredValue) +
                    ' of ' +
                    businessValue.expectedValue +
                    ' value points delivered across ' +
                    businessValue.scoredStories +
                    ' stories.'
                : sv
                  ? 'Koppla berättelser med affärsvärde 1–5 till sprintens uppgifter.'
                  : 'Link stories with business values from 1–5 to tasks in this sprint.'}
            </p>
            {businessValue?.available && (
              <div className="progress-track">
                <span style={{ width: businessValue.deliveredPercent + '%' }} />
              </div>
            )}
          </article>
        </div>
      )}
      <div className="metric-grid">
        {cards.map(([label, value, detail]) => (
          <article key={label}>
            <small>{label}</small>
            <strong>{value}</strong>
            <span>{detail}</span>
          </article>
        ))}
      </div>
      <div className="analysis-grid">
        <article className="analysis-card">
          <div className="analysis-heading">
            <div>
              <small>{sv ? 'FLÖDE' : 'FLOW'}</small>
              <h2>
                {sprintId
                  ? sv
                    ? 'Sprintens burndown'
                    : 'Sprint burndown'
                  : sv
                    ? 'Burndown, 14 dagar'
                    : '14-day burndown'}
              </h2>
            </div>
            <span>
              {burndown.length
                ? `${burndown.at(-1)?.remainingPoints ?? 0} ${sv ? 'poäng kvar' : 'points left'}`
                : '—'}
            </span>
          </div>
          {!burndownReady ? (
            <div className="burndown-empty-state" role="status">
              <span className="burndown-empty-icon" aria-hidden="true">
                {metrics.burndownAvailable === false &&
                Number(metrics.taskCount ?? 0) > 0
                  ? '!'
                  : '✓'}
              </span>
              <div>
                <strong>{burndownEmptyTitle}</strong>
                <p>{burndownEmptyDetail}</p>
              </div>
            </div>
          ) : (
            <>
              <svg
                className="burndown-chart"
                viewBox="0 0 720 250"
                preserveAspectRatio="none"
                role="img"
                aria-label={
                  sv
                    ? 'Burndown: återstående poäng per dag under den valda perioden'
                    : 'Burndown: remaining story points per day during the selected period'
                }
              >
                {chartGridValues.map((value) => {
                  const y =
                    chartBottom - (value / chartMax) * (chartBottom - chartTop);
                  return (
                    <g key={value}>
                      <line
                        className="burndown-gridline"
                        x1={chartLeft}
                        x2={chartRight}
                        y1={y}
                        y2={y}
                      />
                      <text
                        className="burndown-axis-label"
                        x={chartLeft - 10}
                        y={y + 4}
                        textAnchor="end"
                      >
                        {value}
                      </text>
                    </g>
                  );
                })}
                <line
                  className="burndown-ideal"
                  x1={chartLeft}
                  x2={chartRight}
                  y1={chartTop}
                  y2={chartBottom}
                />
                <polyline className="burndown-actual" points={chartPath} />
                {chartCoords.map((point) => (
                  <circle
                    className="burndown-point"
                    key={point.date}
                    cx={point.x}
                    cy={point.y}
                    r="4"
                  >
                    <title>
                      {point.date}: {point.remainingPoints}{' '}
                      {sv ? 'poäng kvar' : 'points remaining'}
                    </title>
                  </circle>
                ))}
                {dateLabelIndexes.map((index) => (
                  <text
                    className="burndown-date-label"
                    key={burndown[index]?.date}
                    x={chartCoords[index]?.x ?? chartLeft}
                    y="226"
                    textAnchor="middle"
                  >
                    {burndown[index]?.date.slice(5)}
                  </text>
                ))}
              </svg>
              <div className="burndown-legend">
                <span>
                  <i className="legend-actual" />
                  {sv ? 'Återstående poäng' : 'Remaining points'}
                </span>
                <span>
                  <i className="legend-ideal" />
                  {sv ? 'Ideal takt' : 'Ideal pace'}
                </span>
                {burndown.at(-1)?.remainingPoints === 0 && (
                  <b>{sv ? 'Allt arbete är klart' : 'All work is complete'}</b>
                )}
              </div>
            </>
          )}
        </article>
        <article className="analysis-card">
          <div className="analysis-heading">
            <div>
              <small>{sv ? 'SPRINTSTATUS' : 'SPRINT HEALTH'}</small>
              <h2>{sv ? 'Leveransrisk' : 'Delivery risk'}</h2>
            </div>
            <b
              className={`health-badge ${health.status === 'At risk' ? 'risk' : health.status === 'Watch WIP' ? 'watch' : ''}`}
            >
              {sv
                ? health.status === 'At risk'
                  ? 'Risk'
                  : health.status === 'Watch WIP'
                    ? 'Håll koll på WIP'
                    : 'Stabil'
                : (health.status ?? 'Healthy')}
            </b>
          </div>
          <p>
            {(health.explanation
              ? localizeMessage(health.explanation, language)
              : undefined) ??
              (sv
                ? 'Ingen risk upptäckt.'
                : 'No current delivery risk detected.')}
          </p>
          <div className="health-stats">
            <span>
              {sv ? 'Pågående' : 'In progress'}{' '}
              <strong>{String(health.workInProgress ?? 0)}</strong>
            </span>
            <span>
              {sv ? 'Blockerade' : 'Blocked'}{' '}
              <strong>{String(health.blockedTasks ?? 0)}</strong>
            </span>
          </div>
          <div className="status-breakdown">
            {Object.entries(statusCounts).map(([status, count]) => (
              <div key={status}>
                <span>
                  {sv
                    ? ((
                        {
                          'To Do': 'Att göra',
                          'In Progress': 'Pågående',
                          Review: 'Granskning',
                          Done: 'Klart',
                        } as Record<string, string>
                      )[status] ?? status)
                    : status}
                </span>
                <b>{count}</b>
              </div>
            ))}
          </div>
        </article>
      </div>
      <details className="metric-method">
        <summary>
          {sv ? 'Så beräknas mätvärdena' : 'How these metrics are calculated'}
        </summary>
        <ul>
          <li>
            <strong>{sv ? 'Cykeltid:' : 'Cycle time:'} </strong>
            {sv
              ? 'från senaste start i Pågår till senaste Klart-händelsen.'
              : 'from the latest In Progress transition to the latest Done transition.'}
          </li>
          <li>
            <strong>{sv ? 'Ledtid:' : 'Lead time:'} </strong>
            {sv
              ? 'från att uppgiften skapades till Klart-händelsen.'
              : 'from task creation to its Done transition.'}
          </li>
          <li>
            <strong>{sv ? 'Genomströmning:' : 'Throughput:'} </strong>
            {sv
              ? sprintId
                ? 'antal uppgifter som slutförts i den valda sprinten.'
                : 'antal uppgifter som slutförts under de senaste 14 dagarna.'
              : sprintId
                ? 'tasks completed in the selected sprint.'
                : 'tasks completed in the last 14 days.'}
          </li>
          <li>
            <strong>{sv ? 'Hastighet:' : 'Velocity:'} </strong>
            {sv
              ? 'story points för uppgifter som har en sparad Klart-händelse.'
              : 'story points on tasks with a persisted Done transition.'}
          </li>
        </ul>
      </details>
      <section className="history-section">
        <div>
          <p className="kicker">{sv ? 'HISTORIK' : 'HISTORY'}</p>
          <h2>{sv ? 'Avslutade sprintar' : 'Completed sprints'}</h2>
        </div>
        {completedSprints.length ? (
          <div className="history-list">
            {completedSprints.map((sprint) => (
              <article key={sprint.id}>
                <div>
                  <strong>{sprint.name}</strong>
                  <small>
                    {sprint.completedAt
                      ? new Date(sprint.completedAt).toLocaleDateString()
                      : sv
                        ? 'Avslutad'
                        : 'Completed'}
                  </small>
                </div>
                <span>
                  {sprint.actualStoryPoints} / {sprint.plannedStoryPoints}{' '}
                  {sv ? 'poäng' : 'pts'}
                </span>
                <small>
                  {sprint.completedTasks}{' '}
                  {sv ? 'uppgifter klara' : 'tasks completed'}
                </small>
              </article>
            ))}
          </div>
        ) : (
          <p className="empty-state">
            {sv
              ? 'Avslutade sprintar visas här när historik finns.'
              : 'Completed sprints will appear here when history is available.'}
          </p>
        )}
      </section>
    </>
  );
}
