import { Fragment, lazy, Suspense, useState } from 'react';
import type { Task } from '../../api/contracts';
import { useT } from '../../shared/i18n/LanguageProvider';
import { VirtualList, VIRTUALIZE_AFTER } from '../../shared/ui/VirtualList';
import { TaskAttachments } from './TaskAttachments';
import { TaskMetadata } from './TaskCollaboration';
import { tasksText } from './i18n';
import type { UseTaskBoardState } from './useTaskBoard';

const TaskGithubLinks = lazy(() => import('./TaskGithubLinks'));

function GithubLinksPanel({ task }: { task: Task }) {
  const t = useT(tasksText);
  const [open, setOpen] = useState(false);
  return (
    <details
      className="task-attachments"
      onToggle={(event) => setOpen(event.currentTarget.open)}
    >
      <summary>{t('githubLinks')}</summary>
      {open && (
        <Suspense>
          <TaskGithubLinks projectId={task.projectId} taskId={task.id} />
        </Suspense>
      )}
    </details>
  );
}
export function Board({
  controller,
  onCustomFields,
  onRisk,
  onSharedText,
}: {
  controller: UseTaskBoardState;
  onCustomFields?: ((task: Task) => void) | undefined;
  onRisk?: (task: Task) => void;
  onSharedText?: (task: Task) => void;
}) {
  const {
    columns,
    tasks,
    dependencyBlocked,
    wipLimits,
    draggedTaskId,
    updateTask,
    setDraggedTaskId,
    labels,
    filtered,
    busyTasks,
    canWrite,
    t,
    editTask,
    toggleBlocked,
    deleteTask,
    setDiscussion,
    sprints,
  } = controller;
  const today = new Date().toISOString().slice(0, 10);
  const activeSprint = sprints.find(
    (sprint) =>
      sprint.status === 'Active' && sprint.endDate && sprint.endDate >= today,
  );
  const columnTasks = (column: Task['status']) =>
    filtered.filter((task) => task.status === column);
  const renderWip = (column: Task['status']) => {
    const limit = wipLimits.find((row) => row.status === column)?.limit;
    if (limit == null) return null;
    const count = tasks.filter((task) => task.status === column).length;
    return (
      <p className={count > limit ? 'board-wip-over' : 'board-wip-count'}>
        {t('wipCount', { count, limit })}
        {count > limit ? ` · ${t('wipOver')}` : ''}
      </p>
    );
  };
  const renderCard = (task: Task) => (
    <article
      aria-busy={busyTasks.has(task.id)}
      draggable={canWrite && !busyTasks.has(task.id)}
      onDragStart={() => setDraggedTaskId(task.id)}
      onDragEnd={() => setDraggedTaskId(undefined)}
      className={`${task.blocked || dependencyBlocked.has(task.id) ? 'is-blocked' : ''} ${task.dueAt && new Date(task.dueAt) < new Date() && task.status !== 'Done' ? 'is-overdue' : ''}`}
    >
      {task.key && <small className="task-key">{task.key}</small>}
      <strong>{task.title}</strong>
      {dependencyBlocked.has(task.id) && (
        <small className="blocked-label">{t('dependencyBlocked')}</small>
      )}
      {task.blocked && (
        <small className="blocked-label">
          {t('blockedLabel')} · {task.blockReason}
        </small>
      )}
      <small>
        {task.storyPoints} {t('points')}
        {task.dueAt ? t('due', { date: new Date(task.dueAt) }) : ''}
      </small>
      {task.assigneeEmail && (
        <small>
          {t('assigned')} · {task.assigneeEmail}
        </small>
      )}
      <label className="task-status-control">
        {t('moveTo')}
        <select
          disabled={!canWrite || busyTasks.has(task.id)}
          aria-label={t('moveTo')}
          value={task.status}
          onChange={(event) =>
            updateTask(task, {
              status: event.target.value as Task['status'],
            })
          }
        >
          {columns.map((item) => (
            <option key={item} value={item}>
              {labels[item]}
            </option>
          ))}
        </select>
      </label>
      <div className="card-actions">
        {onRisk &&
          activeSprint &&
          task.sprintId === activeSprint.id &&
          task.status !== 'Done' && (
            <button type="button" onClick={() => onRisk(task)}>
              {t('lateRisk')}
            </button>
          )}
        {onSharedText && (
          <button type="button" onClick={() => onSharedText(task)}>
            {t('sharedDescription')}
          </button>
        )}
        {onCustomFields && (
          <button type="button" onClick={() => onCustomFields(task)}>
            {t('customFields')}
          </button>
        )}
        <button
          type="button"
          disabled={!canWrite || busyTasks.has(task.id)}
          onClick={() => editTask(task)}
        >
          {t('edit')}
        </button>
        <button
          type="button"
          disabled={!canWrite || busyTasks.has(task.id)}
          onClick={() => toggleBlocked(task)}
        >
          {task.blocked ? t('unblock') : t('block')}
        </button>
        <button
          type="button"
          className="danger-action"
          disabled={!canWrite || busyTasks.has(task.id)}
          onClick={() => deleteTask(task)}
        >
          {t('delete')}
        </button>
      </div>
      {!!task.labels?.length && (
        <div className="task-labels" aria-label={t('labels')}>
          {task.labels.map((label) => (
            <span className="status-badge" key={label}>
              {label}
            </span>
          ))}
        </div>
      )}
      <TaskMetadata
        task={task}
        editable={canWrite && !busyTasks.has(task.id)}
        onChange={(changes) => updateTask(task, changes)}
      />
      <button className="secondary-action" onClick={() => setDiscussion(task)}>
        {t('comments')}
      </button>
      <TaskAttachments taskId={task.id} />
      <GithubLinksPanel task={task} />
    </article>
  );
  return (
    <div className="task-board">
      {columns.map((column) => (
        <section
          key={column}
          onDragOver={(event) => event.preventDefault()}
          onDrop={(event) => {
            event.preventDefault();
            const task = tasks.find((item) => item.id === draggedTaskId);
            if (task && task.status !== column)
              void updateTask(task, { status: column });
            setDraggedTaskId(undefined);
          }}
        >
          <header>
            <span>{labels[column]}</span>
            <b>{columnTasks(column).length}</b>
          </header>
          {renderWip(column)}
          {columnTasks(column).length > VIRTUALIZE_AFTER ? (
            <VirtualList
              items={columnTasks(column)}
              label={labels[column]}
              estimateSize={320}
              getKey={(task) => task.id}
              renderItem={renderCard}
            />
          ) : (
            columnTasks(column).map((task) => (
              <Fragment key={task.id}>{renderCard(task)}</Fragment>
            ))
          )}
          {!filtered.some((task) => task.status === column) && (
            <p className="empty-column">{t('noTasks')}</p>
          )}
        </section>
      ))}
    </div>
  );
}
