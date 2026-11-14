import { useEffect, useState } from 'react';
import { api } from '../api/client';
import type { Language, Task, Sprint } from '../api/contracts';
import { Notice, PageHeader } from './ui';

export function CalendarView({
  projectId,
  language,
}: {
  projectId?: number;
  language: Language;
}) {
  const sv = language === 'SV',
    locale = sv ? 'sv-SE' : 'en-GB';
  const [month, setMonth] = useState(
    () => new Date(new Date().getFullYear(), new Date().getMonth(), 1),
  );
  const [tasks, setTasks] = useState<Task[]>([]);
  const [sprints, setSprints] = useState<Sprint[]>([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  useEffect(() => {
    let live = true;
    setTasks([]);
    setSprints([]);
    setError('');
    if (projectId) {
      setLoading(true);
      Promise.all([
        api<Task[]>(`/tasks?projectId=${projectId}`),
        api<Sprint[]>(`/sprints?projectId=${projectId}`),
      ])
        .then(([t, s]) => {
          if (live) {
            setTasks(t);
            setSprints(s);
          }
        })
        .catch((cause) => {
          if (live) setError(cause.message);
        })
        .finally(() => {
          if (live) setLoading(false);
        });
    }
    return () => {
      live = false;
    };
  }, [projectId]);
  const count = new Date(
    month.getFullYear(),
    month.getMonth() + 1,
    0,
  ).getDate();
  const offset = (month.getDay() + 6) % 7;
  const days = Array.from(
    { length: count },
    (_, i) => new Date(month.getFullYear(), month.getMonth(), i + 1),
  );
  const today = new Date().toLocaleDateString('sv-SE');
  function events(date: Date) {
    const key = date.toLocaleDateString('sv-SE');
    return [
      ...tasks
        .filter(
          (task) =>
            task.dueAt &&
            new Date(task.dueAt).toLocaleDateString('sv-SE') === key,
        )
        .map((task) => ({
          id: 't' + task.id,
          title: task.title,
          kind: sv ? 'Förfaller' : 'Due',
          done: task.status === 'Done',
        })),
      ...sprints
        .filter(
          (sprint) =>
            sprint.startDate &&
            sprint.endDate &&
            key >= sprint.startDate &&
            key <= sprint.endDate,
        )
        .map((sprint) => ({
          id: 's' + sprint.id,
          title: sprint.name,
          kind: sv ? 'Sprint' : 'Sprint',
          done: sprint.status === 'Completed',
        })),
    ];
  }
  return (
    <section className="page">
      <PageHeader
        guide="calendar"
        language={language}
        eyebrow={sv ? 'TEAMETS PLANERING' : 'TEAM SCHEDULE'}
        title={sv ? 'Kalender' : 'Calendar'}
        description={
          sv
            ? 'Sprintar och deadlines på samma plats.'
            : 'Sprint dates and task deadlines in one place.'
        }
      />
      <Notice language={language} error={error} />
      {!projectId ? (
        <div className="empty-state">
          {sv
            ? 'Välj eller skapa ett projekt först.'
            : 'Choose or create a project first.'}
        </div>
      ) : (
        <>
          <div className="collection-actions calendar-toolbar">
            <button
              aria-label={sv ? 'Föregående månad' : 'Previous month'}
              onClick={() =>
                setMonth(
                  (value) =>
                    new Date(value.getFullYear(), value.getMonth() - 1, 1),
                )
              }
            >
              ←
            </button>
            <h2 aria-live="polite">
              {month.toLocaleDateString(locale, {
                month: 'long',
                year: 'numeric',
              })}
            </h2>
            <button
              aria-label={sv ? 'Nästa månad' : 'Next month'}
              onClick={() =>
                setMonth(
                  (value) =>
                    new Date(value.getFullYear(), value.getMonth() + 1, 1),
                )
              }
            >
              →
            </button>
            <button
              onClick={() =>
                setMonth(
                  new Date(new Date().getFullYear(), new Date().getMonth(), 1),
                )
              }
            >
              {sv ? 'Idag' : 'Today'}
            </button>
          </div>
          {loading && (
            <p role="status">{sv ? 'Laddar kalender…' : 'Loading calendar…'}</p>
          )}
          <div className="calendar-weekdays" aria-hidden="true">
            {Array.from({ length: 7 }, (_, i) =>
              new Date(2026, 0, 5 + i).toLocaleDateString(locale, {
                weekday: 'short',
              }),
            ).map((day) => (
              <span key={day}>{day}</span>
            ))}
          </div>
          <div className="calendar-grid">
            {Array.from({ length: offset }, (_, i) => (
              <div className="calendar-spacer" key={'empty' + i} />
            ))}
            {days.map((date) => {
              const key = date.toLocaleDateString('sv-SE'),
                items = events(date);
              return (
                <section
                  className={
                    'calendar-day' +
                    (items.length ? ' has-events' : '') +
                    (key === today ? ' today' : '')
                  }
                  key={key}
                  aria-label={date.toLocaleDateString(locale, {
                    weekday: 'long',
                    day: 'numeric',
                    month: 'long',
                  })}
                >
                  <h3>
                    <time dateTime={key}>
                      {date.getDate()}
                      <span className="calendar-mobile-date">
                        {' '}
                        · {date.toLocaleDateString(locale, { weekday: 'long' })}
                      </span>
                    </time>
                  </h3>
                  {items.map((item) => (
                    <p key={item.id} className={item.done ? 'completed' : ''}>
                      <small>{item.kind}</small>
                      {item.title}
                    </p>
                  ))}
                  {items.length === 0 && (
                    <span className="calendar-empty">
                      {sv ? 'Inget planerat' : 'Nothing scheduled'}
                    </span>
                  )}
                </section>
              );
            })}
          </div>
          {!loading && !days.some((date) => events(date).length) && (
            <p className="empty-state">
              {sv
                ? 'Inga sprintar eller deadlines den här månaden.'
                : 'No sprints or deadlines this month.'}
            </p>
          )}
        </>
      )}
    </section>
  );
}
