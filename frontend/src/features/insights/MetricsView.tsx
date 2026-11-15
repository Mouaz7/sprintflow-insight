import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { sprintsQuery } from '../../api/queries';
import { localizeMessage } from '../../shared/i18n/messages';
import { FlowAnalysis } from './FlowAnalysis';
import { MetricsHistory } from './MetricsHistory';
import { metricsPresentation } from './metricsPresentation';
import { metricsQuery } from './queries';
import { SprintAnalysis } from './SprintAnalysis';

export function MetricsCards({
  projectId,
  language,
}: {
  projectId: number;
  language: 'EN' | 'SV';
}) {
  const sv = language === 'SV';
  const [sprintId, setSprintId] = useState('');
  const sprintList = useQuery(sprintsQuery(projectId));
  const metricsResult = useQuery(metricsQuery(projectId, sprintId));
  const sprints = sprintList.data ?? [];
  const metrics = metricsResult.data;
  const error = metricsResult.error?.message ?? sprintList.error?.message ?? '';
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
  const view = metricsPresentation(metrics, language, sprintId);
  const { query, cards } = view;
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
      {sprintId && <SprintAnalysis view={view} />}
      <div className="metric-grid">
        {cards.map(([label, value, detail]) => (
          <article key={label}>
            <small>{label}</small>
            <strong>{value}</strong>
            <span>{detail}</span>
          </article>
        ))}
      </div>
      <FlowAnalysis view={view} />
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
      <MetricsHistory view={view} />
    </>
  );
}
