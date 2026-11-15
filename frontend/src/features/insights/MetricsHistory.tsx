import type { MetricsPresentation } from './metricsPresentation';
export function MetricsHistory({ view }: { view: MetricsPresentation }) {
  const { sv, completedSprints } = view;
  return (
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
  );
}
