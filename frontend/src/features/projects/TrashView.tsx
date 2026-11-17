import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';
import type { Project, TrashItem } from '../../api/contracts';
import { useLanguage, useT } from '../../shared/i18n/LanguageProvider';
import { Notice, PageHeader, useConfirm } from '../../shared/ui/ui';
import { projectsText } from './i18n';
import { trashQuery } from './queries';
export function TrashView({
  projects,
  userEmail,
  onChanged,
}: {
  projects: Project[];
  userEmail: string;
  onChanged: () => Promise<void>;
}) {
  const t = useT(projectsText);
  const { format } = useLanguage();
  const [writeError, setError] = useState('');
  const [busy, setBusy] = useState('');
  const confirm = useConfirm();
  const trash = useQuery(trashQuery);
  const items = Array.isArray(trash.data) ? trash.data : [];
  const loading = trash.isPending || trash.isFetching;
  const error = writeError || trash.error?.message || '';
  async function change(item: TrashItem, purge = false) {
    if (busy) return;
    if (purge && !(await confirm(t('confirmPurge', { title: item.title }))))
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
      await trash.refetch();
    } catch (cause) {
      setError((cause as Error).message);
    } finally {
      setBusy('');
    }
  }
  const names = {
    project: t('kindProject'),
    task: t('kindTask'),
    story: t('kindStory'),
    sprint: t('kindSprint'),
  };
  return (
    <section className="page">
      <PageHeader
        guide="trash"
        eyebrow={t('recoverWork')}
        title={t('recycleBin')}
        description={t('recycleBinHelp')}
      />
      <Notice error={error} />
      {loading ? (
        <p role="status">{t('loadingTrash')}</p>
      ) : items.length ? (
        <div className="project-directory">
          {items.map((item) => (
            <article className="project-row" key={item.kind + item.id}>
              <span className="status-badge">{names[item.kind]}</span>
              <div className="project-row-main">
                <h2>{item.title}</h2>
                <p>{format.dateTime(item.trashedAt)}</p>
              </div>
              <div className="card-actions">
                <button disabled={!!busy} onClick={() => change(item)}>
                  {t('restore')}
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
                    {t('deletePermanently')}
                  </button>
                )}
              </div>
            </article>
          ))}
        </div>
      ) : (
        <div className="empty-state">
          <h2>{t('trashEmpty')}</h2>
          <p>{t('trashEmptyHelp')}</p>
        </div>
      )}
    </section>
  );
}
