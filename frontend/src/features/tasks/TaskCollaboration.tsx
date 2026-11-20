import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { api } from '../../api/client';
import type { ChecklistStep, Task, TaskComment } from '../../api/contracts';
import { useLanguage, useT } from '../../shared/i18n/LanguageProvider';
import { FormGuide, useFieldExample } from '../../shared/ui/Guidance';
import { tasksText } from './i18n';
import { Drawer, Notice, useConfirm } from '../../shared/ui/ui';
import { MentionInput } from './MentionInput';
import { commentsQuery } from './queries';

export function TaskMetadata({
  task,
  onChange,
  editable = true,
}: {
  task: Task;
  onChange: (changes: Partial<Task>) => Promise<boolean>;
  editable?: boolean;
}) {
  const t = useT(tasksText);
  const fieldExample = useFieldExample();
  const [label, setLabel] = useState('');
  const [step, setStep] = useState('');
  const [busy, setBusy] = useState(false);
  const [pendingChecklist, setPendingChecklist] = useState<ChecklistStep[]>();
  const [editing, setEditing] = useState<string>();
  const [editedLabel, setEditedLabel] = useState('');
  const confirm = useConfirm();
  async function save(changes: Partial<Task>) {
    if (busy) return false;
    setBusy(true);
    if (changes.checklist) setPendingChecklist(changes.checklist);
    try {
      return await onChange(changes);
    } finally {
      setPendingChecklist(undefined);
      setBusy(false);
    }
  }
  const checklist = pendingChecklist ?? task.checklist ?? [];
  return (
    <details className="task-metadata" aria-busy={busy}>
      <summary>
        {t('labelsAndChecklist')}{' '}
        {checklist.length > 0 &&
          `(${checklist.filter((item) => item.completed).length}/${checklist.length})`}
      </summary>
      <div className="task-labels">
        {(task.labels ?? []).map((item) => (
          <span className="status-badge" key={item}>
            {item}
            {editable && (
              <button
                disabled={busy}
                aria-label={`${t('removeLabel')} ${item}`}
                onClick={() =>
                  save({
                    labels: (task.labels ?? []).filter(
                      (value) => value !== item,
                    ),
                  })
                }
              >
                ×
              </button>
            )}
          </span>
        ))}
      </div>
      {editable && (
        <form
          onSubmit={async (event) => {
            event.preventDefault();
            const value = label.trim();
            if (!value || (task.labels ?? []).includes(value)) return;
            if (await save({ labels: [...(task.labels ?? []), value] }))
              setLabel('');
          }}
        >
          <label>
            {t('newLabel')}
            <input
              value={label}
              onChange={(event) => setLabel(event.target.value)}
              maxLength={32}
              placeholder={fieldExample('label')}
              disabled={busy || (task.labels?.length ?? 0) >= 12}
            />
          </label>
          <button
            disabled={busy || !label.trim() || (task.labels?.length ?? 0) >= 12}
          >
            {t('addLabel')}
          </button>
        </form>
      )}
      <ul className="checklist-list">
        {checklist.map((item) => (
          <li key={item.id}>
            <label>
              <input
                type="checkbox"
                checked={item.completed}
                disabled={busy || !editable}
                onChange={(event) =>
                  save({
                    checklist: checklist.map((value) =>
                      value.id === item.id
                        ? { ...value, completed: event.target.checked }
                        : value,
                    ),
                  })
                }
              />
              <span>
                {item.label}
                {item.required && <small> · {t('required')}</small>}
              </span>
            </label>
            {editable && (
              <div className="card-actions">
                <button
                  disabled={busy}
                  onClick={() => {
                    setEditing(item.id);
                    setEditedLabel(item.label);
                  }}
                >
                  {t('rename')}
                </button>
                <button
                  disabled={busy}
                  aria-label={`${t('removeStep')} ${item.label}`}
                  onClick={async () => {
                    if (await confirm(t('confirmRemoveStep')))
                      await save({
                        checklist: checklist.filter(
                          (value) => value.id !== item.id,
                        ),
                      });
                  }}
                >
                  ×
                </button>
              </div>
            )}
            {editing === item.id && (
              <form
                onSubmit={async (event) => {
                  event.preventDefault();
                  if (
                    await save({
                      checklist: checklist.map((value) =>
                        value.id === item.id
                          ? { ...value, label: editedLabel.trim() }
                          : value,
                      ),
                    })
                  )
                    setEditing(undefined);
                }}
              >
                <input
                  aria-label={t('stepName')}
                  required
                  maxLength={180}
                  placeholder={fieldExample('checklist')}
                  value={editedLabel}
                  onChange={(event) => setEditedLabel(event.target.value)}
                />
                <button disabled={busy || !editedLabel.trim()}>
                  {t('save')}
                </button>
                <button type="button" onClick={() => setEditing(undefined)}>
                  {t('cancel')}
                </button>
              </form>
            )}
          </li>
        ))}
      </ul>
      {editable && (
        <form
          onSubmit={async (event) => {
            event.preventDefault();
            if (!step.trim()) return;
            if (
              await save({
                checklist: [
                  ...checklist,
                  {
                    id: crypto.randomUUID(),
                    label: step.trim(),
                    completed: false,
                    required: true,
                  },
                ],
              })
            )
              setStep('');
          }}
        >
          <label>
            {t('newStep')}
            <input
              placeholder={fieldExample('checklist')}
              value={step}
              maxLength={180}
              disabled={busy || checklist.length >= 50}
              onChange={(event) => setStep(event.target.value)}
            />
          </label>
          <button disabled={busy || !step.trim() || checklist.length >= 50}>
            {t('addStep')}
          </button>
        </form>
      )}
      <small>{t('checklistHint')}</small>
    </details>
  );
}

export function TaskDiscussion({
  task,
  userEmail,
  canModerate,
  readOnly,
  onClose,
}: {
  task: Task;
  userEmail: string;
  canModerate: boolean;
  readOnly: boolean;
  onClose: () => void;
}) {
  const t = useT(tasksText);
  const { format } = useLanguage();
  const fieldExample = useFieldExample();
  const queryClient = useQueryClient();
  const commentList = useQuery(commentsQuery(task.id));
  const comments = commentList.data ?? [];
  const loading = commentList.isPending;
  function setComments(update: (items: TaskComment[]) => TaskComment[]) {
    queryClient.setQueryData(commentsQuery(task.id).queryKey, (items) =>
      update(items ?? []),
    );
  }
  const [message, setMessage] = useState('');
  const [editing, setEditing] = useState<TaskComment>();
  // Undefined shows the load error, if any; actions set or clear their own.
  const [actionError, setError] = useState<string>();
  const error = actionError ?? commentList.error?.message ?? '';
  const [busy, setBusy] = useState(false);
  const confirm = useConfirm();
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (busy || !message.trim()) return;
    setBusy(true);
    setError('');
    try {
      const saved = await api<TaskComment>(
        `/tasks/${task.id}/comments${editing ? '/' + editing.id : ''}`,
        {
          method: editing ? 'PUT' : 'POST',
          body: JSON.stringify({
            message: message.trim(),
            version: editing?.version,
          }),
        },
      );
      setComments((items) =>
        editing
          ? items.map((item) => (item.id === saved.id ? saved : item))
          : [...items, saved],
      );
      setMessage('');
      setEditing(undefined);
      window.dispatchEvent(new Event('sprintflow:notifications'));
    } catch (cause) {
      setError(
        cause instanceof Error ? cause.message : 'Could not save comment.',
      );
    } finally {
      setBusy(false);
    }
  }
  async function remove(item: TaskComment) {
    if (busy || !(await confirm(t('confirmDeleteComment')))) return;
    setBusy(true);
    try {
      await api(`/tasks/${task.id}/comments/${item.id}`, { method: 'DELETE' });
      setComments((items) => items.filter((value) => value.id !== item.id));
    } catch (cause) {
      setError((cause as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <Drawer
      title={`${t('comments')} · ${task.title}`}
      dirty={!!message.trim()}
      busy={busy}
      onClose={onClose}
    >
      <Notice error={error} />
      {loading ? (
        <p role="status">{t('loadingComments')}</p>
      ) : (
        <div className="discussion-list">
          {comments.length === 0 && <p>{t('startDiscussion')}</p>}
          {comments.map((item) => (
            <article key={item.id}>
              <header>
                <strong>{item.authorEmail}</strong>
                <time dateTime={item.createdAt}>
                  {format.dateTime(item.createdAt)}
                </time>
              </header>
              <p>{item.message}</p>
              {!readOnly &&
                (canModerate ||
                  item.authorEmail.toLowerCase() ===
                    userEmail.toLowerCase()) && (
                  <div className="card-actions">
                    <button
                      disabled={busy}
                      onClick={async () => {
                        if (
                          message.trim() &&
                          !(await confirm(t('discardDraft')))
                        )
                          return;
                        setEditing(item);
                        setMessage(item.message);
                      }}
                    >
                      {t('edit')}
                    </button>
                    <button
                      className="danger-action"
                      disabled={busy}
                      onClick={() => remove(item)}
                    >
                      {t('delete')}
                    </button>
                  </div>
                )}
            </article>
          ))}
        </div>
      )}
      {!readOnly && (
        <form className="editor-form" onSubmit={submit}>
          <FormGuide topic="comment" />
          <MentionInput
            projectId={task.projectId}
            label={editing ? t('editComment') : t('newComment')}
            value={message}
            onChange={setMessage}
            disabled={busy}
            placeholder={fieldExample('comment')}
          />
          <div className="editor-footer">
            <button
              className="primary-action"
              disabled={busy || !message.trim()}
            >
              {busy
                ? t('saving')
                : editing
                  ? t('saveComment')
                  : t('postComment')}
            </button>
            {editing && (
              <button
                type="button"
                disabled={busy}
                onClick={() => {
                  setEditing(undefined);
                  setMessage('');
                }}
              >
                {t('cancel')}
              </button>
            )}
          </div>
        </form>
      )}
    </Drawer>
  );
}
