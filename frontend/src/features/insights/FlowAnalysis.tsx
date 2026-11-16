import { useLanguage, useT } from '../../shared/i18n/LanguageProvider';
import { localizeMessage } from '../../shared/i18n/messages';
import { flowStatus, healthStatus, insightsText } from './i18n';
import type { MetricsPresentation } from './metricsPresentation';
import { LineChart } from '../../shared/ui/charts/Chart';
import { EmptyState } from '../../shared/ui/States';
export function FlowAnalysis({ view }: { view: MetricsPresentation }) {
  const t = useT(insightsText);
  const { language } = useLanguage();
  const {
    sprintId,
    burndown,
    burndownReady,
    burndownEmptyTitle,
    burndownEmptyDetail,
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
          <EmptyState
            title={burndownEmptyTitle}
            description={burndownEmptyDetail}
          />
        ) : (
          <>
            <LineChart
              title={t('burndownChart')}
              data={burndown.map((day) => ({
                label: day.date,
                values: [day.remainingPoints],
              }))}
              series={[t('remainingPoints')]}
              ideal
            />
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
