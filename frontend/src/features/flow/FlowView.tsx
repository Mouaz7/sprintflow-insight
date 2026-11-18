import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router';
import type { FlowReport, Project } from '../../api/contracts';
import { roleQuery } from '../../api/queries';
import { useT } from '../../shared/i18n/LanguageProvider';
import { PageHeader } from '../../shared/ui/ui';
import { EmptyState, ErrorState, Skeleton } from '../../shared/ui/States';
import { FlowCharts } from './FlowCharts';
import { LimitEditor } from './LimitEditor';
import { flowQuery } from './queries';
import { columnName, flowText } from './i18n';

export function FlowView({ project }: { project: Project | undefined }) {
  const t = useT(flowText);
  return (
    <section className="page flow-page">
      <PageHeader
        guide="flow"
        eyebrow={t('eyebrow')}
        title={t('title')}
        description={t('description')}
      />
      {project ? (
        <ProjectFlow key={project.id} project={project} />
      ) : (
        <EmptyState title={t('noProject')} />
      )}
    </section>
  );
}

function ProjectFlow({ project }: { project: Project }) {
  const t = useT(flowText);
  const [params, setParams] = useSearchParams();
  const requested = Number(params.get('days'));
  const days = [7, 30, 90, 180].includes(requested) ? requested : 30;
  const query = useQuery(flowQuery(project.id, days));
  const role = useQuery(roleQuery(project.id));
  const [editing, setEditing] = useState(false);
  const canManage =
    !project.archivedAt && ['Owner', 'Admin'].includes(role.data?.role ?? '');
  return (
    <>
      <div className="flow-toolbar">
        <label>
          {t('window')}
          <select
            value={days}
            onChange={(event) => {
              const next = new URLSearchParams(params);
              next.set('days', event.target.value);
              setParams(next);
            }}
          >
            {[7, 30, 90, 180].map((count) => (
              <option key={count} value={count}>
                {t('windowDays', { count })}
              </option>
            ))}
          </select>
        </label>
        {canManage && (
          <button className="primary-action" onClick={() => setEditing(true)}>
            {t('configure')}
          </button>
        )}
      </div>
      <p className="flow-help">{t('windowHint')}</p>
      {!role.isPending && !canManage && (
        <p className="flow-help">{t('readonly')}</p>
      )}
      {editing && (
        <LimitEditor projectId={project.id} onClose={() => setEditing(false)} />
      )}
      {query.isError || role.isError ? (
        <ErrorState
          detail={t('failed')}
          onRetry={() => {
            void query.refetch();
            void role.refetch();
          }}
        />
      ) : query.isPending || role.isPending ? (
        <Skeleton label={t('loading')} />
      ) : (
        <FlowDetails data={query.data} />
      )}
    </>
  );
}

function FlowDetails({ data }: { data: FlowReport }) {
  const t = useT(flowText);
  const [limit, setLimit] = useState(50);
  const bands = {
    BELOW_P50: 'below50',
    P50_TO_P85: 'below85',
    P85_TO_P95: 'below95',
    ABOVE_P95: 'above95',
    NO_REFERENCE: 'noReference',
  } as const;
  const law = data.littlesLaw;
  const hasHistory = data.cumulativeFlow.some(
    (day) => day.toDo + day.inProgress + day.review + day.done > 0,
  );
  return (
    <>
      {!!data.tasksWithoutHistory && (
        <p className="flow-warning" role="status">
          {t('missing', { count: data.tasksWithoutHistory })}
        </p>
      )}
      {!hasHistory && (
        <EmptyState title={t('noHistory')} description={t('noHistoryHint')} />
      )}
      <dl className="flow-statistics flow-panel">
        <div>
          <dt>{t('completed')}</dt>
          <dd>{t('number', { value: data.completedTasks })}</dd>
        </div>
        <div>
          <dt>{t('rate')}</dt>
          <dd>{t('number', { value: data.throughputPerDay })}</dd>
        </div>
      </dl>
      <section className="flow-panel">
        <h2>{t('columns')}</h2>
        <p className="flow-help">{t('pressureHint')}</p>
        <div className="flow-columns">
          {data.columns.map((column) => (
            <article
              key={column.status}
              className={column.overLimit ? 'flow-column-over' : ''}
            >
              <h3>{columnName(t, column.status)}</h3>
              <p>
                {t('current')}: <strong>{column.currentWip}</strong> ·{' '}
                {t('limit')}: {column.limit ?? t('noLimit')}
              </p>
              <p>
                {t('averageWip')}: {t('number', { value: column.averageWip })}
              </p>
              {column.overLimit && (
                <p className="flow-warning">{t('overLimit')}</p>
              )}
              {column.bottleneck && (
                <p className="status-badge">{t('pressure')}</p>
              )}
            </article>
          ))}
        </div>
      </section>
      {hasHistory && <FlowCharts data={data} />}
      <section className="flow-panel">
        <h2>
          {t('aging')} ({data.agingWip.length})
        </h2>
        <p className="flow-help">{t('ageHint')}</p>
        {!data.agingWip.length && <p>{t('noAging')}</p>}
        <ul className="flow-work-list">
          {data.agingWip.slice(0, limit).map((task) => (
            <li
              key={task.taskId}
              className={task.band === 'ABOVE_P95' ? 'flow-aging-old' : ''}
            >
              <Link to={`/tasks?task=${encodeURIComponent(task.key)}`}>
                {task.key} · {task.title}
              </Link>
              <span>
                {columnName(t, task.status)} · {t('age')}:{' '}
                {t('number', { value: task.ageHours })} · {t('columnAge')}:{' '}
                {t('number', { value: task.statusAgeHours })}
              </span>
              <span>{t(bands[task.band])}</span>
            </li>
          ))}
        </ul>
        {data.agingWip.length > limit && (
          <button
            className="secondary-action"
            onClick={() => setLimit(limit + 50)}
          >
            {t('more')}
          </button>
        )}
      </section>
      <section className="flow-panel">
        <h2>{t('law')}</h2>
        <p className="flow-help">{t('lawHint')}</p>
        <h3>
          {t(
            law.status === 'CONSISTENT'
              ? 'lawEnough'
              : law.status === 'INCONSISTENT'
                ? 'lawReview'
                : 'lawInsufficient',
          )}
        </h3>
        <dl className="flow-statistics">
          <div>
            <dt>{t('measured')}</dt>
            <dd>{t('number', { value: law.averageWip })}</dd>
          </div>
          <div>
            <dt>{t('expected')}</dt>
            <dd>{t('number', { value: law.expectedWip })}</dd>
          </div>
          <div>
            <dt>{t('ratio')}</dt>
            <dd>
              {law.ratio === null ? '—' : t('number', { value: law.ratio })}
            </dd>
          </div>
        </dl>
        <p className="flow-help">{t('toleranceHint')}</p>
      </section>
    </>
  );
}
