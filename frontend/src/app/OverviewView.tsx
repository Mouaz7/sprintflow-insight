import { useEffect, useState } from 'react';
import { api } from '../api/client';
import type {
  Activity,
  Language,
  Metrics,
  Project,
  Sprint,
  Task,
  WorkspaceView,
} from '../api/contracts';
import { PageHeader } from './ui';
import { InterfaceIcon } from './InterfaceIcons';
import { activitySummary } from './activityText';

export function OverviewView({
  project,
  language,
  onNavigate,
  onNewTask,
}: {
  project?: Project;
  language: Language;
  onNavigate: (view: WorkspaceView) => void;
  onNewTask: () => void;
}) {
  const sv = language === 'SV';
  const [metrics, setMetrics] = useState<Metrics>();
  const [tasks, setTasks] = useState<Task[]>();
  const [sprints, setSprints] = useState<Sprint[]>();
  const [activity, setActivity] = useState<Activity[]>();
  const [failed, setFailed] = useState<string[]>([]);
  const id = project?.id;
  useEffect(() => {
    let cancelled = false;
    setMetrics(undefined);
    setTasks(undefined);
    setSprints(undefined);
    setActivity(undefined);
    setFailed([]);
    if (!id)
      return () => {
        cancelled = true;
      };
    function load<T>(path: string, update: (value: T) => void) {
      void api<T>(path)
        .then((result) => {
          if (!cancelled) update(result);
        })
        .catch(() => {
          if (!cancelled) setFailed((current) => [...current, path]);
        });
    }
    load<Metrics>(`/projects/${id}/metrics`, setMetrics);
    load<Task[]>(`/tasks?projectId=${id}`, setTasks);
    load<Sprint[]>(`/sprints?projectId=${id}`, setSprints);
    load<Activity[]>(`/projects/${id}/activity`, setActivity);
    return () => {
      cancelled = true;
    };
  }, [id]);
  if (!project)
    return (
      <section className="page">
        <PageHeader
          guide="overview"
          language={language}
          eyebrow={sv ? 'DIN ARBETSYTA' : 'YOUR WORKSPACE'}
          title={sv ? 'Välj ett projekt' : 'Choose a project'}
          description={
            sv
              ? 'Börja med en gemensam plats för ditt team.'
              : 'Start with a shared space for your team.'
          }
        />
        <div className="empty-state">
          <InterfaceIcon name="projects" />
          <h2>
            {sv
              ? 'Ditt nästa initiativ börjar här'
              : 'Your next initiative starts here'}
          </h2>
          <p>
            {sv
              ? 'Skapa ett projekt för uppgifter, sprintar och leveransdata.'
              : 'Create a project for tasks, sprints and delivery data.'}
          </p>
          <button
            className="primary-action"
            onClick={() => onNavigate('projects')}
          >
            {sv ? 'Skapa projekt' : 'Create project'}
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
      sv ? 'Uppgifter' : 'Tasks',
      metrics ? String(metrics.taskCount ?? 0) : '—',
      sv ? 'I projektet' : 'In this project',
    ],
    [
      sv ? 'Slutfört' : 'Completion',
      metrics ? `${metrics.completionRate ?? 0}%` : '—',
      sv ? 'Av allt arbete' : 'Of all work',
    ],
    [
      sv ? 'Genomströmning' : 'Throughput',
      metrics ? String(metrics.throughputLast14Days ?? 0) : '—',
      sv ? 'Senaste 14 dagarna' : 'Last 14 days',
    ],
    [
      sv ? 'Leveransstatus' : 'Delivery health',
      health?.status
        ? sv
          ? health.status === 'Healthy'
            ? 'Stabil'
            : health.status === 'At risk'
              ? 'Risk'
              : 'Håll koll'
          : health.status
        : metrics
          ? sv
            ? 'Stabil'
            : 'Healthy'
          : '—',
      sv ? 'Aktuell status' : 'Current status',
    ],
  ];
  function sectionState(path: string, ready: boolean, empty: string) {
    return failed.includes(path) ? (
      <p className="form-message error" role="alert">
        {sv
          ? 'Den här delen kunde inte laddas. Försök ladda om sidan.'
          : 'This section could not load. Try refreshing the page.'}
      </p>
    ) : (
      <p className="quiet-state" role={ready ? undefined : 'status'}>
        {ready ? empty : sv ? 'Laddar…' : 'Loading…'}
      </p>
    );
  }
  return (
    <section className="page dashboard-page">
      <PageHeader
        guide="overview"
        language={language}
        eyebrow={sv ? 'PROJEKTÖVERSIKT' : 'PROJECT OVERVIEW'}
        title={project.name}
        description={
          project.description ||
          (sv
            ? 'Ditt teams nästa steg, på ett ställe.'
            : 'Your team’s next steps, in one place.')
        }
        action={
          <button className="primary-action" onClick={onNewTask}>
            + {sv ? 'Ny uppgift' : 'New task'}
          </button>
        }
      />
      {failed.includes(`/projects/${id}/metrics`) &&
        sectionState(`/projects/${id}/metrics`, false, '')}
      <div className="overview-grid">
        {cards.map(([label, value, detail], index) => (
          <article key={label}>
            <div>
              <span>{label}</span>
              <span className="stat-index">0{index + 1}</span>
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
              <span className="eyebrow">
                {sv ? 'NÄSTA STEG' : 'NEXT STEPS'}
              </span>
              <h2>{sv ? 'Arbetet som pågår' : 'Work in motion'}</h2>
            </div>
            <button className="text-button" onClick={() => onNavigate('tasks')}>
              {sv ? 'Öppna tavlan' : 'Open board'} ↗
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
                    <small>
                      {task.assigneeEmail ||
                        (sv ? 'Ingen ansvarig' : 'Unassigned')}
                    </small>
                  </span>
                  <span className="status-badge">
                    {sv
                      ? {
                          'To Do': 'Att göra',
                          'In Progress': 'Pågår',
                          Review: 'Granskning',
                          Done: 'Klart',
                        }[task.status]
                      : task.status}
                  </span>
                </button>
              ))}
            </div>
          ) : (
            sectionState(
              `/tasks?projectId=${id}`,
              !!tasks,
              sv
                ? 'Inga öppna uppgifter. Nästa steg är ditt.'
                : 'No open tasks. The next step is yours.',
            )
          )}
        </section>
        <section className="dashboard-sprint surface">
          <header className="section-heading">
            <div>
              <span className="eyebrow">
                {sv ? 'TEAMETS FOKUS' : 'TEAM FOCUS'}
              </span>
              <h2>{sv ? 'Aktiv sprint' : 'Active sprint'}</h2>
            </div>
            <InterfaceIcon name="sprints" />
          </header>
          {activeSprint ? (
            <>
              <span className="status-badge">{sv ? 'AKTIV' : 'ACTIVE'}</span>
              <h3>{activeSprint.name}</h3>
              <p>
                {activeSprint.goal ||
                  (sv
                    ? 'Lägg till ett gemensamt sprintmål.'
                    : 'Add a shared sprint goal.')}
              </p>
              <div className="sprint-summary-foot">
                <strong>
                  {activeSprint.totalStoryPoints}{' '}
                  <small>{sv ? 'planerade poäng' : 'planned points'}</small>
                </strong>
                <span>
                  {activeSprint.endDate
                    ? `${sv ? 'Till' : 'Until'} ${activeSprint.endDate}`
                    : '—'}
                </span>
              </div>
            </>
          ) : (
            sectionState(
              `/sprints?projectId=${id}`,
              !!sprints,
              sv ? 'Ingen aktiv sprint ännu.' : 'No active sprint yet.',
            )
          )}
          <button
            className="secondary-action"
            onClick={() => onNavigate('sprints')}
          >
            {sv ? 'Visa sprintar' : 'View sprints'} ↗
          </button>
        </section>
        <section className="dashboard-activity surface">
          <header className="section-heading">
            <div>
              <span className="eyebrow">
                {sv ? 'SENASTE HÄNDELSER' : 'LATEST ACTIVITY'}
              </span>
              <h2>{sv ? 'Från teamet' : 'From the team'}</h2>
            </div>
            <button
              className="text-button"
              onClick={() => onNavigate('updates')}
            >
              {sv ? 'Visa historik' : 'View history'} ↗
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
                      {item.actorEmail} ·{' '}
                      {new Date(item.createdAt).toLocaleString(
                        sv ? 'sv-SE' : 'en-GB',
                      )}
                    </small>
                  </div>
                </article>
              ))}
            </div>
          ) : (
            sectionState(
              `/projects/${id}/activity`,
              !!activity,
              sv
                ? 'Ingen aktivitet i projektet ännu.'
                : 'No activity yet for this project.',
            )
          )}
        </section>
      </div>
    </section>
  );
}
