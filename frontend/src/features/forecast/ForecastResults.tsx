import type {
  ForecastBin,
  ForecastReport,
  ForecastWarning,
} from '../../api/contracts';
import { useT } from '../../shared/i18n/LanguageProvider';
import { EmptyState } from '../../shared/ui/States';
import { BarChart } from '../../shared/ui/charts/Chart';
import { forecastText, type ForecastText } from './i18n';

function warning(
  t: ForecastText,
  data: ForecastReport,
  kind: ForecastWarning,
): string {
  switch (kind) {
    case 'NO_HISTORY':
      return t('noHistory');
    case 'NO_COMPLETIONS':
      return t('noCompletions');
    case 'INSUFFICIENT_HISTORY':
      return t('sparse', { count: data.minimumPeriods });
    case 'MISSING_TASK_HISTORY':
      return t('missing', { count: data.scope.tasksWithoutHistory });
    case 'UNPLANNED_STORIES':
      return t('unplanned', { count: data.scope.unplannedStories });
    case 'ZERO_POINT_TASKS':
      return t('zeroPoints');
    case 'EPIC_USES_PROJECT_HISTORY':
      return t('epicCapacity');
    case 'PAST_SPRINT_DEADLINE':
      return t('overdue');
    case 'TARGET_BEYOND_HORIZON':
      return t('distant');
  }
}

export function ForecastResults({ data }: { data: ForecastReport }) {
  const t = useT(forecastText);
  const unit = t(data.unit === 'ITEMS' ? 'tasks' : 'points');
  const canForecast =
    !data.warnings.includes('NO_HISTORY') &&
    !data.warnings.includes('NO_COMPLETIONS');
  return (
    <>
      {data.warnings.length > 0 && (
        <aside className="forecast-warnings" role="status">
          <ul>
            {data.warnings.map((kind) => (
              <li key={kind}>{warning(t, data, kind)}</li>
            ))}
          </ul>
        </aside>
      )}
      <section className="forecast-panel">
        <h2>{data.scope.epicTitle ?? t('all')}</h2>
        <dl className="forecast-stats">
          <div>
            <dt>
              {t('known')} · {unit}
            </dt>
            <dd>
              {t('number', {
                value:
                  data.unit === 'ITEMS'
                    ? data.scope.openTasks
                    : data.scope.openStoryPoints,
              })}
            </dd>
          </div>
          <div>
            <dt>
              {t('planned')} · {unit}
            </dt>
            <dd>{t('number', { value: data.remaining })}</dd>
          </div>
          <div>
            <dt>{t('simulations')}</dt>
            <dd>{t('number', { value: data.simulations })}</dd>
          </div>
        </dl>
        <p className="forecast-help">{t('method')}</p>
      </section>
      {!canForecast ? (
        <EmptyState
          title={t('needHistory')}
          description={t('needHistoryHint')}
        />
      ) : (
        <>
          <section className="forecast-panel">
            <h2>{t('dates')}</h2>
            <p>{t('dateHint')}</p>
            <dl className="forecast-stats">
              {data.completionDates.map((row) => (
                <div key={row.confidence}>
                  <dt>{t('percent', { value: row.confidence })}</dt>
                  <dd>
                    {row.date
                      ? t('date', { value: row.date })
                      : t('unavailable')}
                  </dd>
                </div>
              ))}
            </dl>
            <Distribution
              title={`${t('dateHistogram')} (${t('days')})`}
              bins={data.completionHistogram}
            />
            {data.beyondHorizon > 0 && (
              <p className="forecast-help">
                {t('censored', {
                  count: data.beyondHorizon,
                  days: data.horizonDays,
                })}
              </p>
            )}
          </section>
          {data.itemsByDate && (
            <section className="forecast-panel">
              <h2>
                {t('byDate')} · {t('date', { value: data.itemsByDate.date })}
              </h2>
              <p>{t('amountHint')}</p>
              <dl className="forecast-stats">
                {data.itemsByDate.items.map((row) => (
                  <div key={row.confidence}>
                    <dt>
                      {t('percent', { value: row.confidence })} · {unit}
                    </dt>
                    <dd>{t('number', { value: row.items })}</dd>
                  </div>
                ))}
              </dl>
              <Distribution
                title={`${t('amountHistogram')} (${unit})`}
                bins={data.itemsByDate.histogram}
              />
            </section>
          )}
          <section className="forecast-panel">
            <h2>
              {t(
                data.target.source === 'ACTIVE_SPRINT'
                  ? 'sprintTarget'
                  : 'target',
              )}
            </h2>
            <p>
              {t('number', { value: data.target.items })} {unit} ·{' '}
              {t('date', { value: data.target.date })}
            </p>
            <p className="forecast-probability">
              {data.target.probability === null
                ? t('unavailable')
                : t('percent', { value: data.target.probability })}
            </p>
            <p className="forecast-help">{t('targetHint')}</p>
          </section>
        </>
      )}
      <section className="forecast-panel">
        <details>
          <summary>
            {t('history')} ({t('number', { value: data.history.length })})
          </summary>
          <div
            className="forecast-history"
            role="region"
            aria-label={t('history')}
          >
            <table>
              <caption>
                {t('history')} · {unit}
              </caption>
              <thead>
                <tr>
                  <th scope="col">{t('period')}</th>
                  <th scope="col">{t('amount')}</th>
                </tr>
              </thead>
              <tbody>
                {data.history.map((row, index) => (
                  <tr key={index}>
                    <th scope="row">
                      {row.label ? `${row.label} · ` : ''}
                      {t('date', { value: row.start })} –{' '}
                      {t('date', { value: row.end })}
                    </th>
                    <td>{t('number', { value: row.count })}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </details>
      </section>
    </>
  );
}

function Distribution({ title, bins }: { title: string; bins: ForecastBin[] }) {
  const t = useT(forecastText);
  return (
    <BarChart
      title={title}
      series={[t('simulations')]}
      data={bins.map((bin) => ({
        label:
          bin.from === bin.to
            ? t('number', { value: bin.from })
            : `${t('number', { value: bin.from })}–${t('number', { value: bin.to })}`,
        values: [bin.count],
      }))}
    />
  );
}
