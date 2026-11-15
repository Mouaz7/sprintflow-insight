import { useLanguage, useT } from '../../shared/i18n/LanguageProvider';
import { localizeMessage } from '../../shared/i18n/messages';
import { flowStatus, healthStatus, insightsText } from './i18n';
import type { MetricsPresentation } from './metricsPresentation';
export function FlowAnalysis({ view }: { view: MetricsPresentation }) {
  const t = useT(insightsText);
  const { language } = useLanguage();
  const {
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
    statusCounts,
  } = view;
  return (
    <div className="analysis-grid">
      <article className="analysis-card">
        <div className="analysis-heading">
          <div>
            <small>{t('flow')}</small>
            <h2>{t(sprintId ? 'sprintBurndown' : 'burndown14')}</h2>
          </div>
          <span>
            {burndown.length
              ? `${burndown.at(-1)?.remainingPoints ?? 0} ${t('pointsLeft')}`
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
              aria-label={t('burndownChart')}
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
                    {point.date}: {point.remainingPoints} {t('pointsRemaining')}
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
                {t('remainingPoints')}
              </span>
              <span>
                <i className="legend-ideal" />
                {t('idealPace')}
              </span>
              {burndown.at(-1)?.remainingPoints === 0 && (
                <b>{t('allWorkComplete')}</b>
              )}
            </div>
          </>
        )}
      </article>
      <article className="analysis-card">
        <div className="analysis-heading">
          <div>
            <small>{t('sprintHealth')}</small>
            <h2>{t('deliveryRisk')}</h2>
          </div>
          <b
            className={`health-badge ${health.status === 'At risk' ? 'risk' : health.status === 'Watch WIP' ? 'watch' : ''}`}
          >
            {healthStatus(
              t,
              language,
              health.status ?? 'Healthy',
              'healthHealthy',
            )}
          </b>
        </div>
        <p>
          {(health.explanation
            ? localizeMessage(health.explanation, language)
            : undefined) ?? t('noRisk')}
        </p>
        <div className="health-stats">
          <span>
            {t('inProgress')}{' '}
            <strong>{String(health.workInProgress ?? 0)}</strong>
          </span>
          <span>
            {t('blocked')} <strong>{String(health.blockedTasks ?? 0)}</strong>
          </span>
        </div>
        <div className="status-breakdown">
          {Object.entries(statusCounts).map(([status, count]) => (
            <div key={status}>
              <span>{flowStatus(t, status)}</span>
              <b>{count}</b>
            </div>
          ))}
        </div>
      </article>
    </div>
  );
}
