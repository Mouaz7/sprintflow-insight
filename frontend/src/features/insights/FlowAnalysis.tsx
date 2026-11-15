import { localizeMessage } from '../../shared/i18n/messages';
import type { MetricsPresentation } from './metricsPresentation';
export function FlowAnalysis({ view }: { view: MetricsPresentation }) {
  const {
    sv,
    sprintId,
    burndown,
    burndownReady,
    metrics,
    burndownEmptyTitle,
    burndownEmptyDetail,
    chartGridValues,
    chartBottom,
    chartMax,
    chartTop,
    chartLeft,
    chartRight,
    chartPath,
    chartCoords,
    dateLabelIndexes,
    health,
    language,
    statusCounts,
  } = view;
  return (
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
  );
}
