import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import type { Task } from '../../api/contracts';
import { ApiError } from '../../api/client';
import { useT } from '../../shared/i18n/LanguageProvider';
import { Drawer, Notice } from '../../shared/ui/ui';
import { DateInput } from '../../shared/ui/DateInput';
import { validDate } from '../../shared/lib/formValues';
import { offlineText } from '../../shared/offline/i18n';
import { enqueueTaskChange } from '../../shared/offline/operations';
import type { MemberCapacity, TimeEntry } from './contracts';
import { timeText } from './i18n';
import { useTimeWrite } from './queries';

export function TimeError({ error }: { error: unknown }) {
  const t = useT(timeText);
  const key =
    error instanceof ApiError && [412, 428].includes(error.status)
      ? 'stale'
      : error instanceof ApiError && [401, 403, 409].includes(error.status)
        ? 'denied'
        : error instanceof ApiError && error.status === 400
          ? 'invalid'
          : 'writeFailed';
  return error ? <Notice error={t(key)} /> : null;
}
function today() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
}
export function LogEditor({
  projectId,
  tasks,
  onClose,
  entry,
  timer = false,
}: {
  projectId: number;
  tasks: Task[];
  onClose: () => void;
  entry?: TimeEntry;
  timer?: boolean;
}) {
  const t = useT(timeText);
  const offline = useT(offlineText);
  const client = useQueryClient();
  const write = useTimeWrite(projectId);
  const [taskId, setTaskId] = useState(entry ? String(entry.taskId) : '');
  const [minutes, setMinutes] = useState(String(entry?.minutes ?? 60));
  const [day, setDay] = useState(entry?.spentOn ?? today());
  const [note, setNote] = useState(entry?.note ?? '');
  const [dirty, setDirty] = useState(false);
  const [queueing, setQueueing] = useState(false);
  const [queueError, setQueueError] = useState('');
  const busy = write.isPending || queueing;
  const title = t(entry ? 'edit' : timer ? 'start' : 'log');
  async function logOffline() {
    const task = tasks.find((item) => item.id === Number(taskId));
    setQueueing(true);
    setQueueError('');
    const queued =
      task &&
      (await enqueueTaskChange(client, task, {
        kind: 'LOG_TIME',
        time: { minutes: Number(minutes), spentOn: day, note },
      }));
    setQueueing(false);
    if (queued) onClose();
    else setQueueError(offline('unavailable'));
  }
  return (
    <Drawer title={title} dirty={dirty} busy={busy} onClose={onClose}>
      <TimeError error={write.error} />
      <Notice error={queueError} />
      <form
        className="editor-form"
        onChange={() => setDirty(true)}
        onSubmit={(event) => {
          event.preventDefault();
          if (busy) return;
          if (!entry && !timer && !navigator.onLine) {
            void logOffline();
            return;
          }
          write.mutate(
            {
              path: entry
                ? `/time-entries/${entry.id}`
                : timer
                  ? '/timer'
                  : '/time-entries',
              method: entry ? 'PUT' : 'POST',
              ...(entry ? { version: entry.version } : {}),
              body: timer
                ? { taskId: Number(taskId), note }
                : entry
                  ? { minutes: Number(minutes), spentOn: day, note }
                  : {
                      taskId: Number(taskId),
                      minutes: Number(minutes),
                      spentOn: day,
                      note,
                    },
            },
            { onSuccess: onClose },
          );
        }}
      >
        <fieldset disabled={busy}>
          {entry ? (
            <p>
              {entry.taskKey} · {entry.taskTitle}
            </p>
          ) : (
            <label>
              {t('task')}
              <select
                required
                value={taskId}
                onChange={(event) => setTaskId(event.target.value)}
              >
                <option value="">{t('chooseTask')}</option>
                {tasks.map((task) => (
                  <option key={task.id} value={task.id}>
                    {task.key} · {task.title}
                  </option>
                ))}
              </select>
            </label>
          )}
          {!timer && (
            <>
              <label>
                {t('minutes')}
                <input
                  type="number"
                  required
                  min={1}
                  max={1440}
                  step={1}
                  value={minutes}
                  onChange={(event) => setMinutes(event.target.value)}
                />
              </label>
              <label>
                {t('spentOn')}
                <DateInput
                  required
                  ariaLabel={t('spentOn')}
                  min="2000-01-01"
                  value={day}
                  onChange={(value) => {
                    setDay(value);
                    setDirty(true);
                  }}
                />
              </label>
            </>
          )}
          <label>
            {t('note')}
            <textarea
              maxLength={500}
              value={note}
              onChange={(event) => setNote(event.target.value)}
            />
          </label>
          {timer && <p className="time-hint">{t('timerHint')}</p>}
          <button className="primary-action" type="submit">
            {t(busy ? 'saving' : 'save')}
          </button>
        </fieldset>
      </form>
    </Drawer>
  );
}
export function EstimateEditor({
  projectId,
  task,
  onClose,
}: {
  projectId: number;
  task: Task;
  onClose: () => void;
}) {
  const t = useT(timeText);
  const write = useTimeWrite(projectId);
  const [minutes, setMinutes] = useState(
    task.estimateMinutes == null ? '' : String(task.estimateMinutes),
  );
  const [dirty, setDirty] = useState(false);
  return (
    <Drawer
      title={t('setEstimate')}
      dirty={dirty}
      busy={write.isPending}
      onClose={onClose}
    >
      <p>
        {task.key} · {task.title}
      </p>
      <p className="time-hint">{t('estimateHelp')}</p>
      <TimeError error={write.error} />
      <form
        className="editor-form"
        onSubmit={(event) => {
          event.preventDefault();
          write.mutate(
            {
              path: `/tasks/${task.id}/time-estimate`,
              method: 'PUT',
              version: task.version ?? 0,
              body: {
                estimateMinutes: minutes === '' ? null : Number(minutes),
              },
            },
            { onSuccess: onClose },
          );
        }}
      >
        <label>
          {t('estimate')}
          <input
            disabled={write.isPending}
            type="number"
            min={0}
            max={600000}
            step={1}
            value={minutes}
            onChange={(event) => {
              setMinutes(event.target.value);
              setDirty(true);
            }}
          />
        </label>
        <button
          className="primary-action"
          disabled={write.isPending}
          type="submit"
        >
          {t(write.isPending ? 'saving' : 'save')}
        </button>
      </form>
    </Drawer>
  );
}
export function CapacityEditor({
  projectId,
  sprintId,
  member,
  onClose,
}: {
  projectId: number;
  sprintId: number;
  member: MemberCapacity;
  onClose: () => void;
}) {
  const t = useT(timeText);
  const write = useTimeWrite(projectId);
  const [minutes, setMinutes] = useState(String(member.minutesPerDay ?? 480));
  const [availability, setAvailability] = useState(
    String(member.availabilityPercent ?? 100),
  );
  const [points, setPoints] = useState(
    member.pointCapacity == null ? '' : String(member.pointCapacity),
  );
  const [days, setDays] = useState(member.daysOff.join('\n'));
  const [dirty, setDirty] = useState(false);
  const [invalid, setInvalid] = useState(false);
  const path = `/sprints/${sprintId}/capacity/${member.userId}`;
  return (
    <Drawer
      title={t('configure')}
      dirty={dirty}
      busy={write.isPending}
      onClose={onClose}
    >
      <p>{member.email}</p>
      <p className="time-hint">{t('capacityHelp')}</p>
      <TimeError error={write.error} />
      <form
        className="editor-form"
        onChange={() => setDirty(true)}
        onSubmit={(event) => {
          event.preventDefault();
          const dates = days.trim()
            ? days
                .split(/[\n,]/)
                .map((value) => value.trim())
                .filter(Boolean)
            : [];
          if (dates.length > 366 || dates.some((value) => !validDate(value))) {
            setInvalid(true);
            return;
          }
          setInvalid(false);
          write.mutate(
            {
              path,
              method: 'PUT',
              version: member.version,
              body: {
                minutesPerDay: Number(minutes),
                availabilityPercent: Number(availability),
                pointCapacity: points === '' ? null : Number(points),
                daysOff: dates,
              },
            },
            { onSuccess: onClose },
          );
        }}
      >
        <fieldset disabled={write.isPending}>
          <label>
            {t('minutesPerDay')}
            <input
              type="number"
              required
              min={0}
              max={1440}
              step={1}
              value={minutes}
              onChange={(event) => setMinutes(event.target.value)}
            />
          </label>
          <label>
            {t('availability')}
            <input
              type="number"
              required
              min={0}
              max={100}
              step={1}
              value={availability}
              onChange={(event) => setAvailability(event.target.value)}
            />
          </label>
          <label>
            {t('pointCapacity')}
            <input
              type="number"
              min={0}
              max={10000}
              step={1}
              value={points}
              onChange={(event) => setPoints(event.target.value)}
            />
          </label>
          <label>
            {t('daysOff')}
            <textarea
              maxLength={4200}
              value={days}
              aria-invalid={invalid || undefined}
              onChange={(event) => {
                setDays(event.target.value);
                setInvalid(false);
              }}
            />
          </label>
          <p className="time-hint">{t('daysOffHelp')}</p>
          {invalid && <p role="alert">{t('invalidDates')}</p>}
          <button className="primary-action" type="submit">
            {t(write.isPending ? 'saving' : 'save')}
          </button>
          {member.configured && (
            <button
              className="secondary-action"
              type="button"
              onClick={() =>
                write.mutate(
                  { path, method: 'DELETE', version: member.version },
                  { onSuccess: onClose },
                )
              }
            >
              {t('clearCapacity')}
            </button>
          )}
        </fieldset>
      </form>
    </Drawer>
  );
}
