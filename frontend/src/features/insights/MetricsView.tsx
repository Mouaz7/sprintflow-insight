import { Skeleton, ErrorState } from '../../shared/ui/States';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { sprintsQuery } from '../../api/queries';
import { useLanguage, useT } from '../../shared/i18n/LanguageProvider';
import { localizeMessage } from '../../shared/i18n/messages';
import { FlowAnalysis } from './FlowAnalysis';
import { insightsText } from './i18n';
import { MetricsHistory } from './MetricsHistory';
import { metricsPresentation } from './metricsPresentation';
import { metricsQuery } from './queries';
import { SprintAnalysis } from './SprintAnalysis';

export function MetricsCards({ projectId }: { projectId: number }) {
  const t = useT(insightsText);
  const { language } = useLanguage();
  const [sprintId, setSprintId] = useState('');
  const sprintList = useQuery(sprintsQuery(projectId));
  const metricsResult = useQuery(metricsQuery(projectId, sprintId));
  const sprints = sprintList.data ?? [];
  const metrics = metricsResult.data;
  const error = metricsResult.error?.message ?? sprintList.error?.message ?? '';
  if (!metrics)
    return error ? (
      <ErrorState
        detail={localizeMessage(error, language)}
        onRetry={() => {
          void metricsResult.refetch();
          void sprintList.refetch();
        }}
      />
    ) : (
      <Skeleton label={t('loadingMetrics')} />
    );
  const view = metricsPresentation(metrics, language, sprintId);
  const { query, cards } = view;
  return (
    <>
      <div className="insight-toolbar">
        <label>
          {t('dataScope')}
          <select
            value={sprintId}
            onChange={(event) => setSprintId(event.target.value)}
          >
            <option value="">{t('entireProject')}</option>
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
        <summary>{t('howCalculated')}</summary>
        <ul>
          <li>
            <strong>{t('cycleTimeLabel')} </strong>
            {t('cycleTimeMethod')}
          </li>
          <li>
            <strong>{t('leadTimeLabel')} </strong>
            {t('leadTimeMethod')}
          </li>
          <li>
            <strong>{t('throughputLabel')} </strong>
            {t(sprintId ? 'throughputMethodSprint' : 'throughputMethod14')}
          </li>
          <li>
            <strong>{t('velocityLabel')} </strong>
            {t('velocityMethod')}
          </li>
        </ul>
      </details>
      <MetricsHistory view={view} />
    </>
  );
}
