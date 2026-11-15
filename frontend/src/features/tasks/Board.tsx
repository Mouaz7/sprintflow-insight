import type { Task } from '../../api/contracts';
import { TaskAttachments } from './TaskAttachments';
import { TaskMetadata } from './TaskCollaboration';
import type { UseTaskBoardState } from './useTaskBoard';
export function Board({ controller }: { controller: UseTaskBoardState }) {
  const {
    columns,
    tasks,
    draggedTaskId,
    updateTask,
    setDraggedTaskId,
    labels,
    filtered,
    busyTasks,
    canWrite,
    sv,
    editTask,
    toggleBlocked,
    deleteTask,
    language,
    setDiscussion,
  } = controller;
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
            <b>{filtered.filter((task) => task.status === column).length}</b>
          </header>
          {filtered
            .filter((task) => task.status === column)
            .map((task) => (
              <article
                key={task.id}
                aria-busy={busyTasks.has(task.id)}
                draggable={canWrite && !busyTasks.has(task.id)}
                onDragStart={() => setDraggedTaskId(task.id)}
                onDragEnd={() => setDraggedTaskId(undefined)}
                className={`${task.blocked ? 'is-blocked' : ''} ${task.dueAt && new Date(task.dueAt) < new Date() && task.status !== 'Done' ? 'is-overdue' : ''}`}
              >
                <strong>{task.title}</strong>
                {task.blocked && (
                  <small className="blocked-label">
                    {sv ? 'BLOCKERAD' : 'BLOCKED'} · {task.blockReason}
                  </small>
                )}
                <small>
                  {task.storyPoints} {sv ? 'poäng' : 'pts'}
                  {task.dueAt
                    ? ` · ${sv ? 'förfaller' : 'due'} ${new Date(task.dueAt).toLocaleDateString()}`
                    : ''}
                </small>
                {task.assigneeEmail && (
                  <small>
                    {sv ? 'Ansvarig' : 'Assigned'} · {task.assigneeEmail}
                  </small>
                )}
                <label className="task-status-control">
                  {sv ? 'Flytta till' : 'Move to'}
                  <select
                    disabled={!canWrite || busyTasks.has(task.id)}
                    aria-label={sv ? 'Flytta till' : 'Move to'}
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
                  <button
                    type="button"
                    disabled={!canWrite || busyTasks.has(task.id)}
                    onClick={() => editTask(task)}
                  >
                    {sv ? 'Redigera' : 'Edit'}
                  </button>
                  <button
                    type="button"
                    disabled={!canWrite || busyTasks.has(task.id)}
                    onClick={() => toggleBlocked(task)}
                  >
                    {task.blocked
                      ? sv
                        ? 'Avblockera'
                        : 'Unblock'
                      : sv
                        ? 'Blockera'
                        : 'Block'}
                  </button>
                  <button
                    type="button"
                    className="danger-action"
                    disabled={!canWrite || busyTasks.has(task.id)}
                    onClick={() => deleteTask(task)}
                  >
                    {sv ? 'Ta bort' : 'Delete'}
                  </button>
                </div>
                {!!task.labels?.length && (
                  <div
                    className="task-labels"
                    aria-label={sv ? 'Etiketter' : 'Labels'}
                  >
                    {task.labels.map((label) => (
                      <span className="status-badge" key={label}>
                        {label}
                      </span>
                    ))}
                  </div>
                )}
                <TaskMetadata
                  task={task}
                  language={language}
                  editable={canWrite && !busyTasks.has(task.id)}
                  onChange={(changes) => updateTask(task, changes)}
                />
                <button
                  className="secondary-action"
                  onClick={() => setDiscussion(task)}
                >
                  {sv ? 'Kommentarer' : 'Comments'}
                </button>
                <TaskAttachments taskId={task.id} language={language} />
              </article>
            ))}
          {!filtered.some((task) => task.status === column) && (
            <p className="empty-column">{sv ? 'Inga uppgifter' : 'No tasks'}</p>
          )}
        </section>
      ))}
    </div>
  );
}
