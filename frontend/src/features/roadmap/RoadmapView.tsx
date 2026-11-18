import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router';
import type { Project } from '../../api/contracts';
import { roleQuery } from '../../api/queries';
import { useT } from '../../shared/i18n/LanguageProvider';
import { EmptyState, ErrorState, Skeleton } from '../../shared/ui/States';
import { PageHeader, Notice, useConfirm } from '../../shared/ui/ui';
import { roadmapText } from './i18n';
import {
  estimatesQuery,
  problemText,
  roadmapQuery,
  useRoadmapActions,
  type RoadmapAction,
} from './queries';
import { RoadmapEditor, type Editor } from './RoadmapEditor';
import { Forecast } from './Forecast';
import { Timeline } from './Timeline';

export function RoadmapView({ project }: { project: Project | undefined }) {
  const t = useT(roadmapText);
  return (
    <section className="page roadmap-page">
      <PageHeader
        guide="roadmap"
        eyebrow={t('eyebrow')}
        title={t('title')}
        description={t('description')}
      />
      {project ? (
        <ProjectRoadmap key={project.id} project={project} />
      ) : (
        <EmptyState title={t('noProject')} />
      )}
    </section>
  );
}

function ProjectRoadmap({ project }: { project: Project }) {
  const t = useT(roadmapText);
  const confirm = useConfirm();
  const roadmap = useQuery(roadmapQuery(project.id));
  const role = useQuery(roleQuery(project.id));
  const estimates = useQuery(estimatesQuery(project.id));
  const actions = useRoadmapActions(project.id);
  const [editor, setEditor] = useState<Editor>();
  const [params, setParams] = useSearchParams();
  const [limit, setLimit] = useState(50);
  const canWrite =
    !project.archivedAt &&
    ['Owner', 'Admin', 'Member'].includes(role.data?.role ?? '');
  if (roadmap.isError || role.isError || estimates.isError)
    return (
      <ErrorState
        detail={t('loadFailed')}
        onRetry={() => {
          void roadmap.refetch();
          void role.refetch();
          void estimates.refetch();
        }}
      />
    );
  if (roadmap.isPending || role.isPending || estimates.isPending)
    return <Skeleton label={t('loading')} />;
  const data = roadmap.data;
  const selected = data.epics.find(
    (row) => row.epic.id === Number(params.get('epic')),
  );
  const selectedId = selected?.epic.id;
  const forecast = selected?.forecast ?? data.project;
  async function remove(action: RoadmapAction, message?: string) {
    if (message && !(await confirm(message))) return;
    try {
      await actions.mutateAsync(action);
    } catch {
      /* Render the problem, retaining the current selection. */
    }
  }
  return (
    <>
      <div className="roadmap-toolbar">
        <label>
          {t('selectEpic')}
          <select
            value={selectedId ?? ''}
            onChange={(event) => {
              const next = new URLSearchParams(params);
              if (event.target.value) next.set('epic', event.target.value);
              else next.delete('epic');
              setParams(next);
            }}
          >
            <option value="">{t('allWork')}</option>
            {data.epics.map((row) => (
              <option key={row.epic.id} value={row.epic.id}>
                {row.epic.title}
              </option>
            ))}
          </select>
        </label>
        {canWrite && (
          <div className="roadmap-actions">
            <button
              className="primary-action"
              onClick={() => setEditor({ kind: 'epic' })}
            >
              {t('newEpic')}
            </button>
            <button
              className="secondary-action"
              onClick={() => setEditor({ kind: 'story' })}
            >
              {t('assignStory')}
            </button>
            <button
              className="secondary-action"
              onClick={() => setEditor({ kind: 'dependency' })}
            >
              {t('addDependency')}
            </button>
            <button
              className="secondary-action"
              onClick={() => setEditor({ kind: 'estimate' })}
            >
              {t('editEstimate')}
            </button>
          </div>
        )}
      </div>
      {!canWrite && <p className="roadmap-help">{t('readonly')}</p>}
      <Notice error={problemText(actions.error, t)} />
      {editor && (
        <RoadmapEditor
          projectId={project.id}
          editor={editor}
          epics={data.epics.map((row) => row.epic)}
          selectedEpic={selectedId}
          onClose={() => setEditor(undefined)}
        />
      )}
      {!data.epics.length ? (
        <EmptyState title={t('noEpics')} description={t('noEpicsHint')} />
      ) : (
        <>
          <Timeline data={data} />
          <div className="roadmap-epics">
            {(selected ? [selected] : data.epics).map((row) => (
              <article className="roadmap-panel" key={row.epic.id}>
                <h2>{row.epic.title}</h2>
                {row.epic.description && (
                  <p className="roadmap-description">{row.epic.description}</p>
                )}
                <progress
                  max="1"
                  value={row.progress}
                  aria-label={`${row.epic.title}: ${t('progress')}`}
                />
                <p>
                  {t('percent', { value: row.progress })} ·{' '}
                  {t('storyProgress', {
                    done: row.donePoints,
                    total: row.totalPoints,
                  })}
                </p>
                {canWrite && (
                  <div className="roadmap-actions">
                    <button
                      className="secondary-action"
                      onClick={() =>
                        setEditor({ kind: 'epic', epic: row.epic })
                      }
                    >
                      {t('edit')}
                    </button>
                    <button
                      className="secondary-action danger-action"
                      disabled={actions.isPending}
                      onClick={() =>
                        void remove(
                          { kind: 'deleteEpic', epic: row.epic },
                          t('deleteConfirm', { title: row.epic.title }),
                        )
                      }
                    >
                      {t('deleteEpic')}
                    </button>
                  </div>
                )}
              </article>
            ))}
          </div>
        </>
      )}
      <Forecast forecast={forecast} hasTarget={!!selected?.epic.targetDate} />
      {!!data.dependencies.externalBlocks.length && (
        <section className="roadmap-panel roadmap-warning">
          <h2>{t('externalBlocks')}</h2>
          <ul>
            {data.dependencies.externalBlocks
              .slice(0, limit)
              .map(({ task, blocker }) => (
                <li key={`${task.id}-${blocker.id}`}>
                  {t('externalBlock', {
                    task: `${task.key} · ${task.title}`,
                    blocker: `${blocker.key} · ${blocker.title}`,
                  })}
                </li>
              ))}
          </ul>
        </section>
      )}
      <section className="roadmap-panel">
        <h2>
          {t('dependencies')} ({data.dependencies.dependencies.length})
        </h2>
        {!data.dependencies.dependencies.length && <p>{t('noDependencies')}</p>}
        <ul className="roadmap-dependencies">
          {data.dependencies.dependencies.slice(0, limit).map((edge) => (
            <li key={edge.id}>
              <span>
                <Link
                  to={`/tasks?task=${encodeURIComponent(edge.blocker.key)}`}
                >
                  {edge.blocker.key} · {edge.blocker.title}
                </Link>{' '}
                →{' '}
                <Link
                  to={`/tasks?task=${encodeURIComponent(edge.blocked.key)}`}
                >
                  {edge.blocked.key} · {edge.blocked.title}
                </Link>
                <small>{t(edge.open ? 'waiting' : 'satisfied')}</small>
              </span>
              {canWrite && (
                <button
                  className="secondary-action danger-action"
                  aria-label={`${t('remove')}: ${edge.blocker.key} → ${edge.blocked.key}`}
                  disabled={actions.isPending}
                  onClick={() =>
                    void remove({ kind: 'removeDependency', id: edge.id })
                  }
                >
                  {t('remove')}
                </button>
              )}
            </li>
          ))}
        </ul>
      </section>
      <section className="roadmap-panel">
        <h2>
          {t('estimates')} ({estimates.data.length})
        </h2>
        <p className="roadmap-help">{t('estimateHint')}</p>
        <ul className="roadmap-dependencies">
          {estimates.data.slice(0, limit).map((estimate) => (
            <li key={estimate.taskId}>
              <span>
                <Link
                  to={`/tasks?task=${encodeURIComponent(estimate.task.key)}`}
                >
                  {estimate.task.key} · {estimate.task.title}
                </Link>{' '}
                · {t('number', { value: estimate.optimistic })} /{' '}
                {t('number', { value: estimate.mostLikely })} /{' '}
                {t('number', { value: estimate.pessimistic })}
              </span>
              {canWrite && (
                <button
                  className="secondary-action danger-action"
                  aria-label={`${t('removeEstimate')}: ${estimate.task.key}`}
                  disabled={actions.isPending}
                  onClick={() =>
                    void remove(
                      { kind: 'clearEstimate', estimate },
                      t('clearEstimateConfirm'),
                    )
                  }
                >
                  {t('removeEstimate')}
                </button>
              )}
            </li>
          ))}
        </ul>
      </section>
      {Math.max(
        data.dependencies.dependencies.length,
        estimates.data.length,
        data.dependencies.externalBlocks.length,
      ) > limit && (
        <button
          className="secondary-action"
          onClick={() => setLimit(limit + 50)}
        >
          {t('showMore')}
        </button>
      )}
    </>
  );
}
