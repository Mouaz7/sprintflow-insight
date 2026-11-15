import { useEffect, useState } from 'react';
import { api } from '../../api/client';
import type { Language, Project, TrashItem } from '../../api/contracts';
import { Notice, PageHeader, useConfirm } from '../../shared/ui/ui';
export function TrashView({
  language,
  projects,
  userEmail,
  onChanged,
}: {
  language: Language;
  projects: Project[];
  userEmail: string;
  onChanged: () => Promise<void>;
}) {
  const sv = language === 'SV';
  const [items, setItems] = useState<TrashItem[]>([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState('');
  const [revision, setRevision] = useState(0);
  const confirm = useConfirm();
  useEffect(() => {
    let live = true;
    setLoading(true);
    api<TrashItem[]>('/workspace/trash')
      .then((data) => {
        if (live) setItems(Array.isArray(data) ? data : []);
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
  }, [revision]);
  async function change(item: TrashItem, purge = false) {
    if (busy) return;
    if (
      purge &&
      !(await confirm(
        sv
          ? `Radera “${item.title}” permanent? Det går inte att ångra.`
          : `Permanently delete “${item.title}”? This cannot be undone.`,
      ))
    )
      return;
    setBusy(item.kind + item.id);
    setError('');
    try {
      await api(
        purge && item.kind === 'project'
          ? `/projects/${item.id}`
          : `/workspace/trash/${item.kind}/${item.id}`,
        {
          method: purge ? 'DELETE' : 'PUT',
          headers: { 'If-Match': `"${item.version}"` },
          ...(purge ? {} : { body: JSON.stringify({ restore: true }) }),
        },
      );
      await onChanged();
      setRevision((value) => value + 1);
    } catch (cause) {
      setError((cause as Error).message);
    } finally {
      setBusy('');
    }
  }
  const names = {
    project: sv ? 'Projekt' : 'Project',
    task: sv ? 'Uppgift' : 'Task',
    story: sv ? 'Berättelse' : 'Story',
    sprint: sv ? 'Sprint' : 'Sprint',
  };
  return (
    <section className="page">
      <PageHeader
        guide="trash"
        language={language}
        eyebrow={sv ? 'ÅTERSTÄLL ARBETE' : 'RECOVER WORK'}
        title={sv ? 'Papperskorg' : 'Recycle bin'}
        description={
          sv
            ? 'Återställ borttaget innehåll. Permanent radering tar också bort tillhörande data.'
            : 'Restore deleted work. Permanent deletion also removes its related data.'
        }
      />
      <Notice language={language} error={error} />
      {loading ? (
        <p role="status">
          {sv ? 'Laddar papperskorg…' : 'Loading recycle bin…'}
        </p>
      ) : items.length ? (
        <div className="project-directory">
          {items.map((item) => (
            <article className="project-row" key={item.kind + item.id}>
              <span className="status-badge">{names[item.kind]}</span>
              <div className="project-row-main">
                <h2>{item.title}</h2>
                <p>
                  {new Date(item.trashedAt).toLocaleString(
                    sv ? 'sv-SE' : 'en-GB',
                  )}
                </p>
              </div>
              <div className="card-actions">
                <button disabled={!!busy} onClick={() => change(item)}>
                  {sv ? 'Återställ' : 'Restore'}
                </button>
                {(item.kind === 'project' ||
                  projects
                    .find((project) => project.id === item.projectId)
                    ?.ownerEmail.toLowerCase() === userEmail.toLowerCase()) && (
                  <button
                    className="danger-action"
                    disabled={!!busy}
                    onClick={() => change(item, true)}
                  >
                    {sv ? 'Radera permanent' : 'Delete permanently'}
                  </button>
                )}
              </div>
            </article>
          ))}
        </div>
      ) : (
        <div className="empty-state">
          <h2>{sv ? 'Papperskorgen är tom' : 'Your recycle bin is empty'}</h2>
          <p>
            {sv
              ? 'Borttaget innehåll visas här tills du återställer eller raderar det permanent.'
              : 'Deleted work appears here until you restore or permanently remove it.'}
          </p>
        </div>
      )}
    </section>
  );
}
