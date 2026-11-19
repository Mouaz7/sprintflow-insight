/* eslint-disable jsx-a11y/no-noninteractive-tabindex -- The report overflow region needs keyboard scrolling. */
import { useEffect, useState } from 'react';
import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router';
import type { Project, Task, User } from '../../api/contracts';
import { roleQuery, sprintsQuery, tasksQuery } from '../../api/queries';
import { useT } from '../../shared/i18n/LanguageProvider';
import { PageHeader, useConfirm } from '../../shared/ui/ui';
import { EmptyState, ErrorState, Skeleton } from '../../shared/ui/States';
import type { TimeEntry } from './contracts';
import { timeText } from './i18n';
import {
  entriesQuery,
  reportQuery,
  timeBase,
  timerQuery,
  useTimeWrite,
} from './queries';
import { EstimateEditor, LogEditor, TimeError } from './TimeEditors';
import { CapacityPanel } from './CapacityPanel';

export function TimeView({
  project,
  user,
}: {
  project: Project | undefined;
  user: User;
}) {
  const t = useT(timeText);
  return (
    <section className="page time-page">
      <PageHeader
        guide="time"
        eyebrow={t('eyebrow')}
        title={t('title')}
        description={t('description')}
      />
      {project ? (
        <ProjectTime key={project.id} project={project} user={user} />
      ) : (
        <EmptyState title={t('noProject')} />
      )}
    </section>
  );
}
function ProjectTime({ project, user }: { project: Project; user: User }) {
  const t = useT(timeText);
  const confirm = useConfirm();
  const [params, setParams] = useSearchParams();
  const sprintId = /^[1-9]\d*$/.test(params.get('sprint') ?? '')
    ? Number(params.get('sprint'))
    : 0;
  const role = useQuery(roleQuery(project.id));
  const sprints = useQuery(sprintsQuery(project.id));
  const tasks = useQuery(tasksQuery(project.id));
  const report = useQuery(reportQuery(project.id, sprintId));
  const entries = useInfiniteQuery(entriesQuery(project.id, sprintId));
  const timer = useQuery(timerQuery(project.id));
  const write = useTimeWrite(project.id);
  const [editor, setEditor] = useState<'log' | 'timer'>();
  const [entry, setEntry] = useState<TimeEntry>();
  const [estimate, setEstimate] = useState<Task>();
  const [limit, setLimit] = useState(50);
  const canWrite =
    !project.archivedAt &&
    ['Owner', 'Admin', 'Member'].includes(role.data?.role ?? '');
  const canManage =
    canWrite && ['Owner', 'Admin'].includes(role.data?.role ?? '');
  const queries = [role, sprints, tasks, report, entries, timer];
  if (queries.some((query) => query.isError))
    return (
      <ErrorState
        detail={t('failed')}
        onRetry={() => {
          queries.forEach((query) => {
            void query.refetch();
          });
        }}
      />
    );
  if (
    !tasks.data ||
    !sprints.data ||
    !report.data ||
    !entries.data ||
    !role.data ||
    timer.isPending
  )
    return <Skeleton label={t('loading')} />;
  const scopedTasks = tasks.data.filter(
    (task) => !sprintId || task.sprintId === sprintId,
  );
  const rows = entries.data.pages.flatMap((page) => page.items);
  const data = report.data;
  return (
    <>
      <div className="time-actions">
        <label>
          {t('sprint')}
          <select
            value={sprintId}
            onChange={(event) => {
              const next = new URLSearchParams(params);
              const id = Number(event.target.value);
              if (id) next.set('sprint', String(id));
              else next.delete('sprint');
              setParams(next);
              setLimit(50);
            }}
          >
            <option value={0}>{t('all')}</option>
            {sprints.data.map((sprint) => (
              <option key={sprint.id} value={sprint.id}>
                {sprint.name}
              </option>
            ))}
          </select>
        </label>
        {canWrite && scopedTasks.length > 0 && (
          <>
            <button className="primary-action" onClick={() => setEditor('log')}>
              {t('log')}
            </button>
            <button
              className="secondary-action"
              disabled={!!timer.data}
              onClick={() => setEditor('timer')}
            >
              {t('start')}
            </button>
          </>
        )}
      </div>
      {!canWrite && <p className="time-hint">{t('readonly')}</p>}
      {!tasks.data.length && <EmptyState title={t('noTasks')} />}
      <section className="time-panel">
        <h2>{t('timer')}</h2>
        <p className="time-hint">{t('timerHint')}</p>
        {timer.data ? (
          <>
            <h3>
              {timer.data.taskKey} · {timer.data.taskTitle}
            </h3>
            {timer.data.startedAt && (
              <>
                <Elapsed started={timer.data.startedAt} />
                <p>{t('started', { value: timer.data.startedAt })}</p>
              </>
            )}
            {canWrite && (
              <div className="time-actions">
                <button
                  className="primary-action"
                  disabled={write.isPending}
                  onClick={() =>
                    write.mutate({
                      path: '/timer/stop',
                      method: 'POST',
                      version: timer.data?.version ?? null,
                    })
                  }
                >
                  {t('stop')}
                </button>
                <button
                  className="secondary-action"
                  disabled={write.isPending}
                  onClick={() => {
                    const running = timer.data;
                    if (running)
                      write.mutate({
                        path: `/time-entries/${running.id}`,
                        method: 'DELETE',
                        version: running.version,
                      });
                  }}
                >
                  {t('discard')}
                </button>
              </div>
            )}
          </>
        ) : (
          <p>{t('noTimer')}</p>
        )}
        <TimeError error={write.error} />
      </section>
      <section className="time-panel">
        <h2>{t('entries')}</h2>
        {!rows.length && <p>{t('noEntries')}</p>}
        <ul className="time-entry-list">
          {rows.map((row) => (
            <li key={row.id}>
              <div>
                <Link
                  to={`/tasks?task=${encodeURIComponent(row.taskKey ?? '')}`}
                >
                  {row.taskKey} · {row.taskTitle}
                </Link>
                <p>
                  {row.userEmail ?? t('deletedPerson')} ·{' '}
                  {t('date', { value: row.spentOn })} ·{' '}
                  {row.running
                    ? t('timer')
                    : t('duration', { minutes: row.minutes ?? 0 })}
                </p>
                {row.note && <p>{row.note}</p>}
              </div>
              {canWrite && (canManage || row.userId === user.id) && (
                <div className="time-actions">
                  {!row.running && (
                    <button
                      className="secondary-action"
                      disabled={write.isPending}
                      onClick={() => setEntry(row)}
                    >
                      {t('edit')}
                    </button>
                  )}
                  <button
                    className="secondary-action"
                    disabled={write.isPending}
                    onClick={() => {
                      void confirm(t('removeConfirm')).then((yes) => {
                        if (yes)
                          write.mutate({
                            path: `/time-entries/${row.id}`,
                            method: 'DELETE',
                            version: row.version,
                          });
                      });
                    }}
                  >
                    {t('remove')}
                  </button>
                </div>
              )}
            </li>
          ))}
        </ul>
        {entries.hasNextPage && (
          <button
            className="secondary-action"
            disabled={entries.isFetchingNextPage}
            onClick={() => void entries.fetchNextPage()}
          >
            {t('more')}
          </button>
        )}
      </section>
      <section className="time-panel">
        <h2>{t('report')}</h2>
        <p className="time-hint">{t('reportHint')}</p>
        <dl className="time-stats">
          <div>
            <dt>{t('estimated')}</dt>
            <dd>{t('number', { value: data.estimatedMinutes })}</dd>
          </div>
          <div>
            <dt>{t('actual')}</dt>
            <dd>{t('number', { value: data.loggedMinutes })}</dd>
          </div>
          <div>
            <dt>{t('variance')}</dt>
            <dd>{t('number', { value: data.varianceMinutes })}</dd>
          </div>
          <div>
            <dt>{t('unestimated')}</dt>
            <dd>{data.unestimatedTasks}</dd>
          </div>
        </dl>
        <a
          className="secondary-action"
          href={`/api${timeBase(project.id)}/time-report.csv${sprintId ? `?sprintId=${sprintId}` : ''}`}
        >
          {t('exportCsv')}
        </a>
        <div
          className="time-table-scroll"
          role="region"
          aria-label={t('report')}
          tabIndex={0}
        >
          <table>
            <caption>{t('report')}</caption>
            <thead>
              <tr>
                <th scope="col">{t('task')}</th>
                <th scope="col">{t('estimated')}</th>
                <th scope="col">{t('actual')}</th>
                <th scope="col">{t('variance')}</th>
                <th scope="col">{t('percent')}</th>
                {canWrite && <th scope="col">{t('setEstimate')}</th>}
              </tr>
            </thead>
            <tbody>
              {data.rows.slice(0, limit).map((row) => (
                <tr key={row.taskId}>
                  <th scope="row">
                    {row.key} · {row.title}
                  </th>
                  <td>{row.estimateMinutes ?? '—'}</td>
                  <td>{row.loggedMinutes}</td>
                  <td>{row.varianceMinutes ?? '—'}</td>
                  <td>{row.variancePercent ?? '—'}</td>
                  {canWrite && (
                    <td>
                      <button
                        className="secondary-action"
                        onClick={() => {
                          const task = tasks.data?.find(
                            (item) => item.id === row.taskId,
                          );
                          if (task) setEstimate(task);
                        }}
                      >
                        {t('setEstimate')}
                      </button>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {data.rows.length > limit && (
          <button
            className="secondary-action"
            onClick={() => setLimit(limit + 50)}
          >
            {t('more')}
          </button>
        )}
      </section>
      {sprintId ? (
        <CapacityPanel
          key={sprintId}
          projectId={project.id}
          sprintId={sprintId}
          userId={user.id}
          canWrite={canWrite}
          canManage={canManage}
        />
      ) : (
        <p className="time-hint">{t('chooseSprint')}</p>
      )}
      {canWrite && editor && (
        <LogEditor
          projectId={project.id}
          tasks={scopedTasks}
          timer={editor === 'timer'}
          onClose={() => setEditor(undefined)}
        />
      )}
      {canWrite && entry && (
        <LogEditor
          projectId={project.id}
          tasks={scopedTasks}
          entry={entry}
          onClose={() => setEntry(undefined)}
        />
      )}
      {canWrite && estimate && (
        <EstimateEditor
          projectId={project.id}
          task={estimate}
          onClose={() => setEstimate(undefined)}
        />
      )}
    </>
  );
}
function Elapsed({ started }: { started: string }) {
  const t = useT(timeText);
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);
  const seconds = Math.max(0, Math.floor((now - Date.parse(started)) / 1000));
  const text = [
    Math.floor(seconds / 3600),
    Math.floor(seconds / 60) % 60,
    seconds % 60,
  ]
    .map((part) => String(part).padStart(2, '0'))
    .join(':');
  return (
    <p>
      {t('elapsed')}: <strong>{text}</strong>
    </p>
  );
}
