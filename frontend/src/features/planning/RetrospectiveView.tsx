import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { api } from '../../api/client';
import type { Language, RetrospectiveEntry } from '../../api/contracts';
import { afterProjectWrite, roleQuery, sprintsQuery } from '../../api/queries';
import { FormGuide, fieldExample } from '../../shared/ui/Guidance';
import { Drawer, Notice, PageHeader, useConfirm } from '../../shared/ui/ui';
import { retrospectiveQuery } from './queries';

export function RetrospectiveView({
  projectId,
  language,
  userEmail,
}: {
  projectId?: number | undefined;
  language: Language;
  userEmail: string;
}) {
  const sv = language === 'SV';
  const queryClient = useQueryClient();
  const enabled = !!projectId;
  const sprintList = useQuery({ ...sprintsQuery(projectId ?? 0), enabled });
  const roleResult = useQuery({ ...roleQuery(projectId ?? 0), enabled });
  const setupError =
    sprintList.error?.message ?? roleResult.error?.message ?? '';
  // Like the former combined request, one failed read hides both.
  const ready = !setupError && !!sprintList.data && !!roleResult.data;
  const sprints = ready ? (sprintList.data ?? []) : [];
  const role = ready ? (roleResult.data?.role ?? 'Viewer') : 'Viewer';
  const [chosenSprintId, setSprintId] = useState<string>();
  const sprintId =
    chosenSprintId ??
    String(
      sprints.find((item) => item.status === 'Completed')?.id ??
        sprints[0]?.id ??
        '',
    );
  const entries = useQuery({
    ...retrospectiveQuery(projectId ?? 0, sprintId),
    enabled: enabled && !!sprintId,
  });
  const items = entries.data ?? [];
  const loading = entries.isFetching && !entries.data;
  const [actionError, setError] = useState('');
  const error = actionError || setupError || entries.error?.message || '';
  const [editor, setEditor] = useState<{ entry?: RetrospectiveEntry }>();
  const [message, setMessage] = useState('');
  const [category, setCategory] =
    useState<RetrospectiveEntry['category']>('well');
  const [busy, setBusy] = useState(false);
  const confirm = useConfirm();
  const reload = () =>
    projectId &&
    afterProjectWrite(
      queryClient,
      projectId,
      retrospectiveQuery(projectId, sprintId).queryKey,
    );
  async function save(event: FormEvent) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setError('');
    try {
      await api(
        `/projects/${projectId}/retrospective${editor?.entry ? '/' + editor.entry.id : ''}`,
        {
          method: editor?.entry ? 'PUT' : 'POST',
          body: JSON.stringify({
            sprintId: Number(sprintId),
            category,
            message: message.trim(),
            version: editor?.entry?.version,
          }),
        },
      );
      setEditor(undefined);
      await reload();
    } catch (cause) {
      setError((cause as Error).message);
    } finally {
      setBusy(false);
    }
  }
  async function remove(entry: RetrospectiveEntry) {
    if (!(await confirm(sv ? 'Ta bort anteckningen?' : 'Delete this note?')))
      return;
    try {
      await api(`/projects/${projectId}/retrospective/${entry.id}`, {
        method: 'DELETE',
      });
      await reload();
    } catch (cause) {
      setError((cause as Error).message);
    }
  }
  const labels = {
    well: sv ? 'Det gick bra' : 'Went well',
    improve: sv ? 'Att förbättra' : 'To improve',
    actions: sv ? 'Nästa steg' : 'Action items',
  };
  return (
    <section className="page">
      <PageHeader
        guide="retrospective"
        language={language}
        eyebrow={sv ? 'LÄR OCH FÖRBÄTTRA' : 'LEARN AND IMPROVE'}
        title={sv ? 'Retrospektiv' : 'Retrospective'}
        description={
          sv
            ? 'Samla lärdomar och konkreta nästa steg för varje sprint.'
            : 'Capture lessons and concrete actions for each sprint.'
        }
        action={
          sprintId && role !== 'Viewer' ? (
            <button
              className="primary-action"
              onClick={() => {
                setMessage('');
                setCategory('well');
                setEditor({});
              }}
            >
              {sv ? 'Ny anteckning' : 'New note'}
            </button>
          ) : undefined
        }
      />
      <Notice language={language} error={editor ? undefined : error} />
      <label>
        {sv ? 'Sprint' : 'Sprint'}
        <select
          aria-label={sv ? 'Sprint' : 'Sprint'}
          value={sprintId}
          onChange={(event) => setSprintId(event.target.value)}
          disabled={!sprints.length}
        >
          <option value="">{sv ? 'Välj sprint' : 'Choose sprint'}</option>
          {sprints.map((sprint) => (
            <option key={sprint.id} value={sprint.id}>
              {sprint.name}
            </option>
          ))}
        </select>
      </label>
      {loading && (
        <p role="status">{sv ? 'Laddar anteckningar…' : 'Loading notes…'}</p>
      )}
      {!sprintId ? (
        <div className="empty-state">
          {sv
            ? 'Skapa en sprint för att börja samla lärdomar.'
            : 'Create a sprint to start collecting lessons.'}
        </div>
      ) : (
        <div className="retrospective-grid">
          {(['well', 'improve', 'actions'] as const).map((key) => (
            <section className="analysis-card" key={key}>
              <h2>{labels[key]}</h2>
              {items
                .filter((entry) => entry.category === key)
                .map((entry) => (
                  <article className="retro-note" key={entry.id}>
                    <p>{entry.message}</p>
                    <small>{entry.authorEmail}</small>
                    {role !== 'Viewer' &&
                      (entry.authorEmail.toLowerCase() ===
                        userEmail.toLowerCase() ||
                        ['Owner', 'Admin'].includes(role)) && (
                        <div className="card-actions">
                          <button
                            onClick={() => {
                              setEditor({ entry });
                              setMessage(entry.message);
                              setCategory(entry.category);
                            }}
                          >
                            {sv ? 'Redigera' : 'Edit'}
                          </button>
                          <button
                            className="danger-action"
                            onClick={() => remove(entry)}
                          >
                            {sv ? 'Ta bort' : 'Delete'}
                          </button>
                        </div>
                      )}
                  </article>
                ))}
              {!items.some((entry) => entry.category === key) && (
                <p>{sv ? 'Inga anteckningar ännu.' : 'No notes yet.'}</p>
              )}
            </section>
          ))}
        </div>
      )}
      {editor && (
        <Drawer
          title={
            editor.entry
              ? sv
                ? 'Redigera anteckning'
                : 'Edit note'
              : sv
                ? 'Ny anteckning'
                : 'New note'
          }
          language={language}
          dirty={
            message !== (editor.entry?.message ?? '') ||
            category !== (editor.entry?.category ?? 'well')
          }
          busy={busy}
          onClose={() => setEditor(undefined)}
        >
          <form className="editor-form" onSubmit={save}>
            <FormGuide topic="retro" language={language} />
            <label>
              {sv ? 'Kategori' : 'Category'}
              <select
                aria-label={sv ? 'Kategori' : 'Category'}
                value={category}
                onChange={(event) =>
                  setCategory(
                    event.target.value as RetrospectiveEntry['category'],
                  )
                }
              >
                {Object.entries(labels).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </label>
            <label>
              {sv ? 'Anteckning' : 'Note'}
              <textarea
                aria-label={sv ? 'Anteckning' : 'Note'}
                required
                maxLength={1000}
                placeholder={fieldExample('retro', language)}
                value={message}
                rows={6}
                onChange={(event) => setMessage(event.target.value)}
              />
            </label>
            <Notice language={language} error={error} />
            <button
              className="primary-action"
              disabled={busy || !message.trim()}
            >
              {sv ? 'Spara anteckning' : 'Save note'}
            </button>
          </form>
        </Drawer>
      )}
    </section>
  );
}
