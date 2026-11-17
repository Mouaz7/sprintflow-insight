import { Link } from 'react-router';
import type { PertForecast } from '../../api/contracts';
import { useT } from '../../shared/i18n/LanguageProvider';
import { roadmapText } from './i18n';

export function Forecast({
  forecast,
  hasTarget,
}: {
  forecast: PertForecast;
  hasTarget: boolean;
}) {
  const t = useT(roadmapText);
  const complete =
    forecast.estimatedTasks === forecast.openTasks &&
    !forecast.unplannedStories;
  return (
    <section className="roadmap-panel">
      <h2>{t('forecast')}</h2>
      <p className="roadmap-help">{t('forecastHint')}</p>
      <p>
        {t('estimateCoverage', {
          estimated: forecast.estimatedTasks,
          total: forecast.openTasks,
        })}
      </p>
      {!!forecast.unplannedStories && (
        <p>{t('unplanned', { count: forecast.unplannedStories })}</p>
      )}
      {!complete ? (
        <p className="roadmap-warning" role="status">
          {t('incomplete')}
        </p>
      ) : !forecast.openTasks ? (
        <p>{t('nothingRemaining')}</p>
      ) : null}
      <dl className="roadmap-statistics">
        <div>
          <dt>{t('expectedDays')}</dt>
          <dd>{t('number', { value: forecast.expectedDays })}</dd>
        </div>
        <div>
          <dt>{t('deviation')}</dt>
          <dd>{t('number', { value: forecast.standardDeviationDays })}</dd>
        </div>
        <div>
          <dt>{t('expectedFinish')}</dt>
          <dd>
            {forecast.expectedFinish
              ? t('date', { value: forecast.expectedFinish })
              : '—'}
          </dd>
        </div>
        <div>
          <dt>{t('probability')}</dt>
          <dd>
            {forecast.probabilityOnTime === null
              ? '—'
              : t('percent', { value: forecast.probabilityOnTime })}
          </dd>
        </div>
      </dl>
      {!hasTarget && <p className="roadmap-help">{t('setTarget')}</p>}
      {!!forecast.criticalPath.length && (
        <details>
          <summary>
            {t('criticalPath')} ({forecast.criticalPath.length})
          </summary>
          <p className="roadmap-help">{t('criticalHint')}</p>
          <ol className="roadmap-critical-path">
            {forecast.criticalPath.map((task) => (
              <li key={task.id}>
                <Link to={`/tasks?task=${encodeURIComponent(task.key)}`}>
                  {task.key} · {task.title}
                </Link>
              </li>
            ))}
          </ol>
        </details>
      )}
    </section>
  );
}
