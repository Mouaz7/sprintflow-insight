import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import type {
  FlowReport,
  ForecastReport,
  Sprint,
  User,
} from '../../api/contracts';
import { api } from '../../api/client';
import { queryKeys, tasksQuery } from '../../api/queries';
import { useT } from '../../shared/i18n/LanguageProvider';
import { ErrorState, Skeleton } from '../../shared/ui/States';
import { CumulativeFlowChart, LineChart } from '../../shared/ui/charts/Chart';
import type { WidgetType } from './contracts';
import { dashboardText } from './i18n';
export function WidgetContent({
  type,
  projectId,
  user,
  sprint,
}: {
  type: WidgetType;
  projectId: number;
  user: User;
  sprint: Sprint | undefined;
}) {
  if (type === 'SPRINT_GOAL') return <SprintGoal sprint={sprint} />;
  if (type === 'MY_TASKS') return <MyTasks projectId={projectId} user={user} />;
  if (type === 'BURNDOWN')
    return <Burndown projectId={projectId} sprint={sprint} />;
  if (type === 'FORECAST') return <Forecast projectId={projectId} />;
  return <Flow type={type} projectId={projectId} />;
}
function SprintGoal({ sprint }: { sprint: Sprint | undefined }) {
  const t = useT(dashboardText);
  return (
    <>
      {sprint && <h3>{sprint.name}</h3>}
      <p>{!sprint ? t('noSprint') : sprint.goal || t('noGoal')}</p>
      <Link to="/sprints">{t('sprints')}</Link>
    </>
  );
}
function MyTasks({ projectId, user }: { projectId: number; user: User }) {
  const t = useT(dashboardText);
  const query = useQuery(tasksQuery(projectId));
  if (query.isError)
    return (
      <ErrorState detail={t('failed')} onRetry={() => void query.refetch()} />
    );
  if (!query.data) return <Skeleton label={t('loading')} />;
  const own = query.data.filter(
    (task) =>
      task.assigneeEmail?.toLowerCase() === user.email.toLowerCase() &&
      task.status !== 'Done',
  );
  return (
    <>
      <p className="dashboard-hint">{t('myHint')}</p>
      {own.length ? (
        <ul className="dashboard-tasks">
          {own.slice(0, 20).map((task) => (
            <li key={task.id}>
              <Link
                to={`/tasks?task=${encodeURIComponent(task.key ?? String(task.id))}`}
              >
                {task.key} · {task.title}
              </Link>
            </li>
          ))}
        </ul>
      ) : (
        <p>{t('noTasks')}</p>
      )}
      <Link to="/tasks">{t('tasks')}</Link>
    </>
  );
}
function Burndown({
  projectId,
  sprint,
}: {
  projectId: number;
  sprint: Sprint | undefined;
}) {
  const t = useT(dashboardText);
  const query = useQuery({
    queryKey: ['project', projectId, 'metrics', String(sprint?.id ?? '')],
    enabled: !!sprint,
    queryFn: ({ signal }) =>
      api<{
        burndownAvailable?: boolean;
        burndown: { date: string; remainingPoints: number }[];
      }>(`/projects/${projectId}/metrics?sprintId=${sprint?.id}`, { signal }),
  });
  if (!sprint) return <p>{t('noSprint')}</p>;
  if (query.isError)
    return (
      <ErrorState detail={t('failed')} onRetry={() => void query.refetch()} />
    );
  if (!query.data) return <Skeleton label={t('loading')} />;
  return (
    <>
      <h3>{sprint.name}</h3>
      {query.data.burndownAvailable !== false &&
      query.data.burndown?.length >= 2 ? (
        <LineChart
          title={t('BURNDOWN')}
          series={[t('remaining')]}
          data={query.data.burndown.map((day) => ({
            label: day.date,
            values: [day.remainingPoints],
          }))}
        />
      ) : (
        <p>{t('noBurndown')}</p>
      )}
      <Link to="/metrics">{t('metrics')}</Link>
    </>
  );
}
function Flow({ type, projectId }: { type: 'CFD' | 'WIP'; projectId: number }) {
  const t = useT(dashboardText);
  const query = useQuery({
    queryKey: [...queryKeys.flow(projectId), 30],
    queryFn: ({ signal }) =>
      api<FlowReport>(`/projects/${projectId}/flow?days=30`, { signal }),
  });
  if (query.isError)
    return (
      <ErrorState detail={t('failed')} onRetry={() => void query.refetch()} />
    );
  if (!query.data) return <Skeleton label={t('loading')} />;
  const data = query.data;
  return (
    <>
      <p className="dashboard-hint">{t('days')}</p>
      {type === 'CFD' ? (
        <CumulativeFlowChart
          title={t('CFD')}
          data={data.cumulativeFlow.map((day) => ({
            label: day.date,
            values: [day.done, day.review, day.inProgress, day.toDo],
          }))}
          series={[t('done'), t('review'), t('inProgress'), t('todo')]}
        />
      ) : (
        <ul className="dashboard-wip">
          {data.columns.map((column) => (
            <li key={column.status}>
              <strong>
                {column.status === 'In Progress'
                  ? t('inProgress')
                  : column.status === 'Review'
                    ? t('review')
                    : column.status === 'Done'
                      ? t('done')
                      : t('todo')}
              </strong>
              <p>
                {t('current')}: {t('number', { value: column.currentWip })} ·{' '}
                {t('limit')}: {column.limit ?? t('unlimited')}
              </p>
              {column.overLimit && (
                <p className="dashboard-warning">{t('over')}</p>
              )}
            </li>
          ))}
        </ul>
      )}
      <Link to="/flow?days=30">{t('flow')}</Link>
    </>
  );
}
function Forecast({ projectId }: { projectId: number }) {
  const t = useT(dashboardText);
  const query = useQuery({
    queryKey: [...queryKeys.forecast(projectId), ''],
    queryFn: ({ signal }) =>
      api<ForecastReport>(`/projects/${projectId}/forecast`, { signal }),
  });
  if (query.isError)
    return (
      <ErrorState detail={t('failed')} onRetry={() => void query.refetch()} />
    );
  if (!query.data) return <Skeleton label={t('loading')} />;
  const data = query.data;
  const unavailable =
    data.warnings.includes('NO_HISTORY') ||
    data.warnings.includes('NO_COMPLETIONS');
  return (
    <>
      <p className="dashboard-hint">{t('forecastHint')}</p>
      {unavailable ? (
        <p>{t('noForecast')}</p>
      ) : (
        <>
          {data.warnings.length > 0 && (
            <p className="dashboard-warning">{t('sparse')}</p>
          )}
          <dl className="dashboard-dates">
            {data.completionDates.map((row) => (
              <div key={row.confidence}>
                <dt>
                  {t('confidence')} {t('percent', { value: row.confidence })}
                </dt>
                <dd>
                  {row.date
                    ? t('formatDate', { value: row.date })
                    : t('beyond')}
                </dd>
              </div>
            ))}
          </dl>
        </>
      )}
      <Link to="/forecast">{t('forecast')}</Link>
    </>
  );
}
