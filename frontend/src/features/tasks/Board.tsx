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
    exportQuery,
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
    <div
      className="task-board"
      data-show-matches={
        exportQuery && draggedTaskId === undefined ? true : undefined
      }
    >
      {columns.map((column) => {
        const items = filtered.filter((task) => task.status === column);
        const wip = renderWip(column);
        return (
          <section
            key={column}
            data-empty={!items.length && wip === null ? true : undefined}
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
              <h2>{labels[column]}</h2>
              <b>{items.length}</b>
            </header>
            {wip}
            {items.length > VIRTUALIZE_AFTER ? (
              <VirtualList
                items={items}
                label={labels[column]}
                estimateSize={320}
                getKey={(task) => task.id}
                renderItem={renderCard}
              />
            ) : (
              items.map((task) => (
                <Fragment key={task.id}>{renderCard(task)}</Fragment>
              ))
            )}
            {!items.length && <p className="empty-column">{t('noTasks')}</p>}
          </section>
        );
      })}
    </div>
  );
}
