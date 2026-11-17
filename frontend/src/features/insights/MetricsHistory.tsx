import { useT } from '../../shared/i18n/LanguageProvider';
import { insightsText } from './i18n';
import type { MetricsPresentation } from './metricsPresentation';
export function MetricsHistory({ view }: { view: MetricsPresentation }) {
  const { completedSprints } = view;
  const t = useT(insightsText);
  return (
    <section className="history-section">
      <div>
        <p className="kicker">{t('history')}</p>
        <h2>{t('completedSprints')}</h2>
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
                    : t('completed')}
                </small>
              </div>
              <span>
                {sprint.actualStoryPoints} / {sprint.plannedStoryPoints}{' '}
                {t('pts')}
              </span>
              <small>
                {sprint.completedTasks} {t('tasksCompleted')}
              </small>
            </article>
          ))}
        </div>
      ) : (
        <p className="empty-state">{t('noHistory')}</p>
      )}
    </section>
  );
}
