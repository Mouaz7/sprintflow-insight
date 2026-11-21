import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Suspense, useState, type FormEvent } from 'react';
import { api } from '../../api/client';
import type { RetrospectiveEntry } from '../../api/contracts';
import { afterProjectWrite, roleQuery, sprintsQuery } from '../../api/queries';
import {
  SharedTextDrawer,
  useSharedTextActive,
} from '../../shared/collaboration/entry';
import { useT } from '../../shared/i18n/LanguageProvider';
import { FormGuide, useFieldExample } from '../../shared/ui/Guidance';
import { Drawer, Notice, PageHeader, useConfirm } from '../../shared/ui/ui';
import { planningText } from './i18n';
import { retrospectiveQuery } from './queries';

export function RetrospectiveView({
  projectId,
  userEmail,
}: {
  projectId?: number | undefined;
  userEmail: string;
}) {
  const t = useT(planningText);
  const fieldExample = useFieldExample();
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
  const [sharedEntry, setSharedEntry] = useState<number>();
  const shared = useSharedTextActive(
    projectId && editor?.entry
      ? {
          projectId,
          target: 'RETROSPECTIVE',
          id: editor.entry.id,
        }
      : undefined,
  );
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
            message: shared ? message : message.trim(),
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
    if (!(await confirm(t('confirmDeleteNote')))) return;
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
    well: t('wentWell'),
    improve: t('toImprove'),
    actions: t('actionItems'),
  };
  return (
    <section className="page">
      <PageHeader
        guide="retrospective"
        eyebrow={t('retroEyebrow')}
        title={t('retrospective')}
        description={t('retroDescription')}
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
              {t('newNote')}
            </button>
          ) : undefined
        }
      />
      <Notice error={editor ? undefined : error} />
      <label>
        {t('sprint')}
        <select
          aria-label={t('sprint')}
          value={sprintId}
          onChange={(event) => setSprintId(event.target.value)}
          disabled={!sprints.length}
        >
          <option value="">{t('chooseSprint')}</option>
          {sprints.map((sprint) => (
            <option key={sprint.id} value={sprint.id}>
              {sprint.name}
            </option>
          ))}
        </select>
      </label>
      {loading && <p role="status">{t('loadingNotes')}</p>}
      {!sprintId ? (
        <div className="empty-state">{t('createSprintFirst')}</div>
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
                    <button
                      type="button"
                      onClick={() => setSharedEntry(entry.id)}
                    >
                      {t('sharedNote')}
                    </button>
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
                            {t('edit')}
                          </button>
                          <button
                            className="danger-action"
                            onClick={() => remove(entry)}
                          >
                            {t('delete')}
                          </button>
                        </div>
                      )}
                  </article>
                ))}
              {!items.some((entry) => entry.category === key) && (
                <p>{t('noNotes')}</p>
              )}
            </section>
          ))}
        </div>
      )}
      {projectId && sharedEntry && (
        <Suspense fallback={<p role="status">{t('loadingNotes')}</p>}>
          <SharedTextDrawer
            key={sharedEntry}
            scope={{ projectId, target: 'RETROSPECTIVE', id: sharedEntry }}
            userEmail={userEmail}
            onClose={() => setSharedEntry(undefined)}
            onProjected={() => void reload()}
          />
        </Suspense>
      )}
      {editor && (
        <Drawer
          title={t(editor.entry ? 'editNote' : 'newNote')}
          dirty={
            message !== (editor.entry?.message ?? '') ||
            category !== (editor.entry?.category ?? 'well')
          }
          busy={busy}
          onClose={() => setEditor(undefined)}
        >
          <form className="editor-form" onSubmit={save}>
            <FormGuide topic="retro" />
            <label>
              {t('category')}
              <select
                aria-label={t('category')}
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
              {t('note')}
              <textarea
                aria-label={t('note')}
                readOnly={shared}
                required
                maxLength={1000}
                placeholder={fieldExample('retro')}
                value={message}
                rows={6}
                onChange={(event) => setMessage(event.target.value)}
              />
            </label>
            <Notice error={error} />
            <button
              className="primary-action"
              disabled={busy || !message.trim()}
            >
              {t('saveNote')}
            </button>
          </form>
        </Drawer>
      )}
    </section>
  );
}
