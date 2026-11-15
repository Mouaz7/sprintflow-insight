import { useEffect, useState, type FormEvent } from 'react';
import { api } from '../../api/client';
import type {
  ChecklistStep,
  Language,
  Task,
  TaskComment,
} from '../../api/contracts';
import { FormGuide, fieldExample } from '../../shared/ui/Guidance';
import { Drawer, Notice, useConfirm } from '../../shared/ui/ui';

export function TaskMetadata({
  task,
  language,
  onChange,
  editable = true,
}: {
  task: Task;
  language: Language;
  onChange: (changes: Partial<Task>) => Promise<boolean>;
  editable?: boolean;
}) {
  const sv = language === 'SV';
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
        {sv ? 'Etiketter och checklista' : 'Labels and checklist'}{' '}
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
                aria-label={`${sv ? 'Ta bort etikett' : 'Remove label'} ${item}`}
                onClick={() =>
                  save({
                    labels: task.labels?.filter((value) => value !== item),
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
            {sv ? 'Ny etikett' : 'New label'}
            <input
              value={label}
              onChange={(event) => setLabel(event.target.value)}
              maxLength={32}
              placeholder={fieldExample('label', language)}
              disabled={busy || (task.labels?.length ?? 0) >= 12}
            />
          </label>
          <button
            disabled={busy || !label.trim() || (task.labels?.length ?? 0) >= 12}
          >
            {sv ? 'Lägg till etikett' : 'Add label'}
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
                {item.required && <small> · {sv ? 'Krävs' : 'Required'}</small>}
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
                  {sv ? 'Byt namn' : 'Rename'}
                </button>
                <button
                  disabled={busy}
                  aria-label={`${sv ? 'Ta bort steg' : 'Remove step'} ${item.label}`}
                  onClick={async () => {
                    if (
                      await confirm(
                        sv ? 'Ta bort steget?' : 'Remove this step?',
                      )
                    )
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
                  aria-label={sv ? 'Stegets namn' : 'Step name'}
                  required
                  maxLength={180}
                  placeholder={fieldExample('checklist', language)}
                  value={editedLabel}
                  onChange={(event) => setEditedLabel(event.target.value)}
                />
                <button disabled={busy || !editedLabel.trim()}>
                  {sv ? 'Spara' : 'Save'}
                </button>
                <button type="button" onClick={() => setEditing(undefined)}>
                  {sv ? 'Avbryt' : 'Cancel'}
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
            {sv ? 'Nytt steg' : 'New step'}
            <input
              placeholder={fieldExample('checklist', language)}
              value={step}
              maxLength={180}
              disabled={busy || checklist.length >= 50}
              onChange={(event) => setStep(event.target.value)}
            />
          </label>
          <button disabled={busy || !step.trim() || checklist.length >= 50}>
            {sv ? 'Lägg till steg' : 'Add step'}
          </button>
        </form>
      )}
      <small>
        {sv
          ? 'Slutför alla obligatoriska steg innan uppgiften markeras som klar.'
          : 'Complete all required steps before marking this task Done.'}
      </small>
    </details>
  );
}

export function TaskDiscussion({
  task,
  language,
  userEmail,
  canModerate,
  readOnly,
  onClose,
}: {
  task: Task;
  language: Language;
  userEmail: string;
  canModerate: boolean;
  readOnly: boolean;
  onClose: () => void;
}) {
  const sv = language === 'SV';
  const [comments, setComments] = useState<TaskComment[]>([]);
  const [message, setMessage] = useState('');
  const [editing, setEditing] = useState<TaskComment>();
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const confirm = useConfirm();
  useEffect(() => {
    let live = true;
    api<TaskComment[]>(`/tasks/${task.id}/comments`)
      .then((items) => {
        if (live) setComments(items);
      })
      .catch((cause) => {
        if (live) setError(cause.message);
      })
      .finally(() => {
        if (live) setLoading(false);
      });
    return () => {
      live = false;
    };
  }, [task.id]);
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
    if (
      busy ||
      !(await confirm(sv ? 'Ta bort kommentaren?' : 'Delete this comment?'))
    )
      return;
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
      title={`${sv ? 'Kommentarer' : 'Comments'} · ${task.title}`}
      language={language}
      dirty={!!message.trim()}
      busy={busy}
      onClose={onClose}
    >
      <Notice language={language} error={error} />
      {loading ? (
        <p role="status">{sv ? 'Laddar kommentarer…' : 'Loading comments…'}</p>
      ) : (
        <div className="discussion-list">
          {comments.length === 0 && (
            <p>
              {sv
                ? 'Starta diskussionen om den här uppgiften.'
                : 'Start the discussion on this task.'}
            </p>
          )}
          {comments.map((item) => (
            <article key={item.id}>
              <header>
                <strong>{item.authorEmail}</strong>
                <time dateTime={item.createdAt}>
                  {new Date(item.createdAt).toLocaleString(
                    sv ? 'sv-SE' : 'en-GB',
                  )}
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
                          !(await confirm(
                            sv ? 'Kasta ditt utkast?' : 'Discard your draft?',
                          ))
                        )
                          return;
                        setEditing(item);
                        setMessage(item.message);
                      }}
                    >
                      {sv ? 'Redigera' : 'Edit'}
                    </button>
                    <button
                      className="danger-action"
                      disabled={busy}
                      onClick={() => remove(item)}
                    >
                      {sv ? 'Ta bort' : 'Delete'}
                    </button>
                  </div>
                )}
            </article>
          ))}
        </div>
      )}
      {!readOnly && (
        <form className="editor-form" onSubmit={submit}>
          <FormGuide topic="comment" language={language} />
          <label>
            {editing
              ? sv
                ? 'Redigera kommentar'
                : 'Edit comment'
              : sv
                ? 'Ny kommentar'
                : 'New comment'}
            <textarea
              aria-label={
                editing
                  ? sv
                    ? 'Redigera kommentar'
                    : 'Edit comment'
                  : sv
                    ? 'Ny kommentar'
                    : 'New comment'
              }
              required
              maxLength={1000}
              rows={4}
              placeholder={fieldExample('comment', language)}
              value={message}
              onChange={(event) => setMessage(event.target.value)}
              disabled={busy}
            />
          </label>
          <div className="editor-footer">
            <button
              className="primary-action"
              disabled={busy || !message.trim()}
            >
              {busy
                ? sv
                  ? 'Sparar…'
                  : 'Saving…'
                : editing
                  ? sv
                    ? 'Spara kommentar'
                    : 'Save comment'
                  : sv
                    ? 'Skicka kommentar'
                    : 'Post comment'}
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
                {sv ? 'Avbryt' : 'Cancel'}
              </button>
            )}
          </div>
        </form>
      )}
    </Drawer>
  );
}
