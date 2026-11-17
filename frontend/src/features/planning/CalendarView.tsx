import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { sprintsQuery, tasksQuery } from '../../api/queries';
import { useLanguage, useT } from '../../shared/i18n/LanguageProvider';
import { Notice, PageHeader } from '../../shared/ui/ui';
import { planningText } from './i18n';

// ISO calendar-day key (YYYY-MM-DD) in local time; not user-facing text.
const dayKey = (date: Date) => date.toLocaleDateString('en-CA');

export function CalendarView({
  projectId,
}: {
  projectId?: number | undefined;
}) {
  const { format } = useLanguage();
  const t = useT(planningText);
  const [month, setMonth] = useState(
    () => new Date(new Date().getFullYear(), new Date().getMonth(), 1),
  );
  const enabled = !!projectId;
  const taskList = useQuery({ ...tasksQuery(projectId ?? 0), enabled });
  const sprintList = useQuery({ ...sprintsQuery(projectId ?? 0), enabled });
  const error = taskList.error?.message ?? sprintList.error?.message ?? '';
  const loading = enabled && (taskList.isPending || sprintList.isPending);
  // Like the former combined request, one failed list hides both.
  const tasks = error ? [] : (taskList.data ?? []);
  const sprints = error ? [] : (sprintList.data ?? []);
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
  const today = dayKey(new Date());
  function events(date: Date) {
    const key = dayKey(date);
    return [
      ...tasks
        .filter((task) => task.dueAt && dayKey(new Date(task.dueAt)) === key)
        .map((task) => ({
          id: 't' + task.id,
          title: task.title,
          kind: t('due'),
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
          kind: t('sprint'),
          done: sprint.status === 'Completed',
        })),
    ];
  }
  return (
    <section className="page">
      <PageHeader
        guide="calendar"
        eyebrow={t('calendarEyebrow')}
        title={t('calendar')}
        description={t('calendarDescription')}
      />
      <Notice error={error} />
      {!projectId ? (
        <div className="empty-state">{t('chooseProjectFirst')}</div>
      ) : (
        <>
          <div className="collection-actions calendar-toolbar">
            <button
              aria-label={t('previousMonth')}
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
              {format.date(month, { month: 'long', year: 'numeric' })}
            </h2>
            <button
              aria-label={t('nextMonth')}
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
              {t('today')}
            </button>
          </div>
          {loading && <p role="status">{t('loadingCalendar')}</p>}
          <div className="calendar-weekdays" aria-hidden="true">
            {Array.from({ length: 7 }, (_, i) =>
              format.date(new Date(2026, 0, 5 + i), { weekday: 'short' }),
            ).map((day) => (
              <span key={day}>{day}</span>
            ))}
          </div>
          <div className="calendar-grid">
            {Array.from({ length: offset }, (_, i) => (
              <div className="calendar-spacer" key={'empty' + i} />
            ))}
            {days.map((date) => {
              const key = dayKey(date),
                items = events(date);
              return (
                <section
                  className={
                    'calendar-day' +
                    (items.length ? ' has-events' : '') +
                    (key === today ? ' today' : '')
                  }
                  key={key}
                  aria-label={format.date(date, {
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
                        · {format.date(date, { weekday: 'long' })}
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
                      {t('nothingScheduled')}
                    </span>
                  )}
                </section>
              );
            })}
          </div>
          {!loading && !days.some((date) => events(date).length) && (
            <p className="empty-state">{t('nothingThisMonth')}</p>
          )}
        </>
      )}
    </section>
  );
}
