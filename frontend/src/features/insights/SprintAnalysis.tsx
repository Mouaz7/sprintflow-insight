import { useLanguage, useT } from '../../shared/i18n/LanguageProvider';
import { insightsText } from './i18n';
import type { MetricsPresentation } from './metricsPresentation';
export function SprintAnalysis({ view }: { view: MetricsPresentation }) {
  const { sprintPace, businessValue } = view;
  const t = useT(insightsText);
  const { language } = useLanguage();
  return (
    <div className="analysis-grid">
      <article className="analysis-card">
        <div className="analysis-heading">
          <div>
            <small>{t('sprintPace')}</small>
            <h2>{t('deliveryAgainstPlan')}</h2>
          </div>
          <b
            className={
              sprintPace?.status === 'Behind'
                ? 'health-badge risk'
                : 'health-badge'
            }
          >
            {sprintPace?.available
              ? sprintPace.status === 'On track'
                ? t('onTrack')
                : language === 'EN'
                  ? sprintPace.status
                  : t('behind')
              : t('datesNeeded')}
          </b>
        </div>
        <p>
          {sprintPace?.available
            ? t('paceSummary', {
                delivered: String(sprintPace.deliveredStoryPoints),
                planned: String(sprintPace.plannedStoryPoints),
                expected: String(sprintPace.expectedStoryPoints),
              })
            : t('paceMissing')}
        </p>
        {sprintPace?.available && (
          <div className="progress-track">
            <span
              style={{
                width:
                  (sprintPace.plannedStoryPoints
                    ? Math.min(
                        100,
                        ((sprintPace.deliveredStoryPoints ?? 0) /
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
            <small>{t('businessValue')}</small>
            <h2>{t('valueDelivered')}</h2>
          </div>
          <b>
            {businessValue?.available
              ? String(businessValue.deliveredPercent) + '%'
              : '—'}
          </b>
        </div>
        <p>
          {businessValue?.available
            ? t('valueSummary', {
                delivered: String(businessValue.deliveredValue),
                expected: String(businessValue.expectedValue),
                stories: String(businessValue.scoredStories),
              })
            : t('valueMissing')}
        </p>
        {businessValue?.available && (
          <div className="progress-track">
            <span style={{ width: businessValue.deliveredPercent + '%' }} />
          </div>
        )}
      </article>
    </div>
  );
}
