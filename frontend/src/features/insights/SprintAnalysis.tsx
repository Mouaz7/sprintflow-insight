import type { MetricsPresentation } from './metricsPresentation';
export function SprintAnalysis({ view }: { view: MetricsPresentation }) {
  const { sv, sprintPace, businessValue } = view;
  return (
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
  );
}
