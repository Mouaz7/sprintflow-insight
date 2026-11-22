import { useQuery } from '@tanstack/react-query';
import type { Project, WorkspaceView } from '../../api/contracts';
import { sprintsQuery, tasksQuery } from '../../api/queries';
import { useLanguage, useT } from '../../shared/i18n/LanguageProvider';
import { InterfaceIcon } from '../../shared/ui/InterfaceIcons';
import { PageHeader } from '../../shared/ui/ui';
import { activitySummary } from './activityText';
import { boardStatus, healthStatus, insightsText } from './i18n';
import { activityQuery, metricsQuery } from './queries';

export function OverviewView({
  project,
  onNavigate,
  onNewTask,
}: {
  project?: Project | undefined;
  onNavigate: (view: WorkspaceView) => void;
  onNewTask: () => void;
}) {
  const t = useT(insightsText);
  const { language, format } = useLanguage();
  const id = project?.id;
  const enabled = id !== undefined;
  const projectId = id ?? 0;
  const metricsResult = useQuery({
    ...metricsQuery(projectId, ''),
    enabled,
  });
  const tasksResult = useQuery({ ...tasksQuery(projectId), enabled });
  const sprintsResult = useQuery({ ...sprintsQuery(projectId), enabled });
  const activityResult = useQuery({ ...activityQuery(projectId), enabled });
  const metrics = metricsResult.data;
  const tasks = tasksResult.data;
  const sprints = sprintsResult.data;
  const activity = activityResult.data;
  const failed = [
    metricsResult.isError && `/projects/${id}/metrics`,
    tasksResult.isError && `/tasks?projectId=${id}`,
    sprintsResult.isError && `/sprints?projectId=${id}`,
    activityResult.isError && `/projects/${id}/activity`,
  ].filter((path): path is string => !!path);
  if (!project)
    return (
      <section className="page">
        <PageHeader
          guide="overview"
          eyebrow={t('yourWorkspace')}
          title={t('chooseProject')}
          description={t('sharedSpace')}
        />
        <div className="empty-state">
          <InterfaceIcon name="projects" />
          <h2>{t('nextInitiative')}</h2>
          <p>{t('createProjectHint')}</p>
          <button
            className="primary-action"
            onClick={() => onNavigate('projects')}
          >
            {t('createProject')}
          </button>
        </div>
      </section>
    );
  const health = metrics?.sprintHealth as { status?: string } | undefined;
  const activeSprint = sprints?.find((s) => s.status === 'Active');
  const work = tasks?.filter((t) => t.status !== 'Done').slice(0, 5);
  const recent = activity
    ?.slice()
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    .slice(0, 4);
  const cards = [
    [
      t('tasks'),
      metrics ? String(metrics.taskCount ?? 0) : '—',
      t('inThisProject'),
      'tasks',
    ],
    [
      t('completion'),
      metrics ? `${metrics.completionRate ?? 0}%` : '—',
      t('ofAllWork'),
      'overview',
    ],
    [
      t('throughput'),
      metrics ? String(metrics.throughputLast14Days ?? 0) : '—',
      t('last14Days'),
      'flow',
    ],
    [
      t('deliveryHealth'),
      health?.status
        ? healthStatus(t, language, health.status, 'healthWatch')
        : metrics
          ? t('healthHealthy')
          : '—',
      t('currentStatus'),
      'metrics',
    ],
  ] as const;
  function sectionState(path: string, ready: boolean, empty: string) {
    return failed.includes(path) ? (
      <p className="form-message error" role="alert">
        {t('sectionFailed')}
      </p>
    ) : (
      <p className="quiet-state" role={ready ? undefined : 'status'}>
        {ready ? empty : t('loading')}
      </p>
    );
  }
  return (
    <section className="page dashboard-page">
      <PageHeader
        guide="overview"
        eyebrow={t('projectOverview')}
        title={project.name}
        description={project.description || t('teamNextSteps')}
        action={
          <button className="primary-action" onClick={onNewTask}>
            + {t('newTask')}
          </button>
        }
      />
      {failed.includes(`/projects/${id}/metrics`) &&
        sectionState(`/projects/${id}/metrics`, false, '')}
      <div className="overview-grid">
        {cards.map(([label, value, detail, icon]) => (
          <article key={label}>
            <div>
              <span>{label}</span>
              <span className="stat-icon">
                <InterfaceIcon name={icon} />
              </span>
            </div>
            <strong>{value}</strong>
            <small>{detail}</small>
          </article>
        ))}
      </div>
      <div className="dashboard-layout">
        <section className="dashboard-work surface">
          <header className="section-heading">
            <div>
              <span className="eyebrow">{t('nextSteps')}</span>
              <h2>{t('workInMotion')}</h2>
            </div>
            <button className="text-button" onClick={() => onNavigate('tasks')}>
              {t('openBoard')} ↗
            </button>
          </header>
          {work?.length ? (
            <div className="dashboard-task-list">
              {work.map((task) => (
                <button
                  type="button"
                  key={task.id}
                  onClick={() => onNavigate('tasks')}
                >
                  <span
                    className={
                      'task-state-dot state-' +
                      task.status.replaceAll(' ', '-').toLowerCase()
                    }
                  />
                  <span>
                    <strong>{task.title}</strong>
                    <small>{task.assigneeEmail || t('unassigned')}</small>
                  </span>
                  <span className="status-badge">
                    {boardStatus(t, task.status)}
                  </span>
                </button>
              ))}
            </div>
          ) : (
            sectionState(`/tasks?projectId=${id}`, !!tasks, t('noOpenTasks'))
          )}
        </section>
        <section className="dashboard-sprint surface">
          <header className="section-heading">
            <div>
              <span className="eyebrow">{t('teamFocus')}</span>
              <h2>{t('activeSprint')}</h2>
            </div>
            <InterfaceIcon name="sprints" />
          </header>
          {activeSprint ? (
            <>
              <span className="status-badge">{t('activeBadge')}</span>
              <h3>{activeSprint.name}</h3>
              <p>{activeSprint.goal || t('addSprintGoal')}</p>
              <div className="sprint-summary-foot">
                <strong>
                  {activeSprint.totalStoryPoints}{' '}
                  <small>{t('plannedPoints')}</small>
                </strong>
                <span>
                  {activeSprint.endDate
                    ? t('until', { date: activeSprint.endDate })
                    : '—'}
                </span>
              </div>
            </>
          ) : (
            sectionState(
              `/sprints?projectId=${id}`,
              !!sprints,
              t('noActiveSprint'),
            )
          )}
          <button
            className="secondary-action"
            onClick={() => onNavigate('sprints')}
          >
            {t('viewSprints')} ↗
          </button>
        </section>
        <section className="dashboard-activity surface">
          <header className="section-heading">
            <div>
              <span className="eyebrow">{t('latestActivity')}</span>
              <h2>{t('fromTheTeam')}</h2>
            </div>
            <button
              className="text-button"
              onClick={() => onNavigate('updates')}
            >
              {t('viewHistory')} ↗
            </button>
          </header>
          {recent?.length ? (
            <div className="dashboard-event-list">
              {recent.map((item) => (
                <article key={item.id}>
                  <span className="event-dot" />
                  <div>
                    <strong>{activitySummary(item, language)}</strong>
                    <small>
                      {item.actorEmail} · {format.dateTime(item.createdAt)}
                    </small>
                  </div>
                </article>
              ))}
            </div>
          ) : (
            sectionState(
              `/projects/${id}/activity`,
              !!activity,
              t('noActivity'),
            )
          )}
        </section>
      </div>
    </section>
  );
}
