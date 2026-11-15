import { useState, type FormEvent } from 'react';
import { api } from '../../api/client';
import type { Project, User } from '../../api/contracts';
import { useLanguage, useT } from '../../shared/i18n/LanguageProvider';
import { FormGuide, useFieldExample } from '../../shared/ui/Guidance';
import { InterfaceIcon } from '../../shared/ui/InterfaceIcons';
import { Drawer, Notice, PageHeader, useConfirm } from '../../shared/ui/ui';
import { projectsText } from './i18n';
import { MemberManager } from './MemberManager';

export function ProjectsView({
  projects,
  project,
  user,
  onChanged,
  onSelect,
}: {
  projects: Project[];
  project?: Project | undefined;
  user: User;
  onChanged: () => Promise<void>;
  onSelect: (id: number) => void;
}) {
  const t = useT(projectsText);
  const confirm = useConfirm();
  const [editor, setEditor] = useState<{ project?: Project | undefined }>();
  const [error, setError] = useState('');
  const [query, setQuery] = useState('');
  const [showArchived, setShowArchived] = useState(false);
  async function archive(item: Project) {
    setError('');
    try {
      await api(`/projects/${item.id}/archive`, {
        method: 'PUT',
        body: JSON.stringify({ archived: !item.archivedAt }),
      });
      await onChanged();
    } catch (cause) {
      setError((cause as Error).message);
    }
  }
  const [removing, setRemoving] = useState<number>();
  async function remove(item: Project) {
    if (
      removing ||
      !(await confirm(t('confirmMoveToTrash', { name: item.name })))
    )
      return;
    setRemoving(item.id);
    setError('');
    try {
      await api(`/workspace/trash/project/${item.id}`, {
        method: 'PUT',
        headers: { 'If-Match': `"${item.version}"` },
        body: JSON.stringify({ restore: false }),
      });
      await onChanged();
    } catch (cause) {
      setError(
        cause instanceof Error ? cause.message : 'Could not delete project.',
      );
    } finally {
      setRemoving(undefined);
    }
  }
  const filtered = projects
    .filter((p) => !!p.archivedAt === showArchived)
    .filter((p) =>
      `${p.name} ${p.description ?? ''}`
        .toLowerCase()
        .includes(query.toLowerCase()),
    );
  return (
    <section className="page projects-page">
      <PageHeader
        guide="projects"
        eyebrow={t('directory')}
        title={t('title')}
        description={t('description')}
        action={
          <button className="primary-action" onClick={() => setEditor({})}>
            + {t('createProject')}
          </button>
        }
      />
      <Notice error={error} />
      <div className="collection-actions">
        <button
          aria-pressed={!showArchived}
          onClick={() => setShowArchived(false)}
        >
          {t('activeProjects')}
        </button>
        <button
          aria-pressed={showArchived}
          onClick={() => setShowArchived(true)}
        >
          {t('archivedProjects')}
        </button>
      </div>
      <div className="directory-toolbar">
        <input
          type="search"
          aria-label={t('searchProjects')}
          placeholder={t('searchPlaceholder')}
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <span>{t('projectCount', { count: filtered.length })}</span>
      </div>
      <div className="project-directory">
        {filtered.map((item, index) => (
          <article
            className={
              'project-row' + (item.id === project?.id ? ' selected' : '')
            }
            key={item.id}
          >
            <span className="project-index">
              {String(index + 1).padStart(2, '0')}
            </span>
            <div className="project-row-main">
              <span className="eyebrow">
                {t(item.id === project?.id ? 'selectedProject' : 'project')}
              </span>
              <h2>{item.name}</h2>
              <p>{item.description || t('noDescription')}</p>
              <small>
                {t('owner')} · {item.ownerEmail}
              </small>
            </div>
            <div className="card-actions">
              <button onClick={() => archive(item)}>
                {t(item.archivedAt ? 'unarchive' : 'archive')}
              </button>
              <button type="button" onClick={() => onSelect(item.id)}>
                {t(item.id === project?.id ? 'selected' : 'select')}
              </button>
              <button
                type="button"
                disabled={!!item.archivedAt}
                onClick={() => setEditor({ project: item })}
              >
                {t('edit')}
              </button>
              <button
                className="danger-action"
                type="button"
                disabled={
                  removing !== undefined ||
                  item.ownerEmail.toLowerCase() !== user.email.toLowerCase()
                }
                title={
                  item.ownerEmail.toLowerCase() !== user.email.toLowerCase()
                    ? t('ownerOnlyDelete')
                    : undefined
                }
                onClick={() => remove(item)}
              >
                {t('delete')}
              </button>
            </div>
          </article>
        ))}
      </div>
      {!filtered.length && (
        <div className="empty-state">
          <InterfaceIcon name="projects" />
          <h2>{t(showArchived ? 'noArchived' : 'noProjects')}</h2>
          <p>
            {t(
              query.trim()
                ? 'tryAnotherSearch'
                : showArchived
                  ? 'archivedAppearHere'
                  : projects.some((item) => item.archivedAt)
                    ? 'allArchived'
                    : 'createFirst',
            )}
          </p>
        </div>
      )}
      {project && (
        <section className="selected-project-details">
          <header>
            <span className="eyebrow">{t('details')}</span>
            <h2>{t('information')}</h2>
            <p>{project.description}</p>
          </header>
          <MemberManager
            project={project}
            userEmail={user.email}
            onChanged={onChanged}
          />
        </section>
      )}
      {editor && (
        <ProjectEditor
          key={editor.project?.id ?? 'new'}
          project={editor.project}
          onClose={() => setEditor(undefined)}
          onSaved={async (saved) => {
            await onChanged();
            if (!editor.project) {
              setQuery('');
              setShowArchived(false);
              onSelect(saved.id);
            }
          }}
        />
      )}
    </section>
  );
}

function ProjectEditor({
  project,
  onClose,
  onSaved,
}: {
  project?: Project | undefined;
  onClose: () => void;
  onSaved: (saved: Project) => Promise<void>;
}) {
  const t = useT(projectsText);
  const { language } = useLanguage();
  const fieldExample = useFieldExample();
  const [name, setName] = useState(project?.name ?? '');
  const [description, setDescription] = useState(project?.description ?? '');
  const [template, setTemplate] = useState('Blank');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const dirty =
    name !== (project?.name ?? '') ||
    description !== (project?.description ?? '') ||
    (!project && template !== 'Blank');
  const [persisted, setPersisted] = useState(project);
  async function save(event: FormEvent) {
    event.preventDefault();
    if (busy) return;
    if (!name.trim()) {
      setError(t('enterName'));
      return;
    }
    setBusy(true);
    setError('');
    try {
      let savedProject = persisted;
      // A successful write followed by a failed refresh must not create a duplicate.
      if (
        !persisted ||
        persisted.name !== name.trim() ||
        (persisted.description ?? '') !== description.trim()
      ) {
        const saved = await api<Project>(
          persisted
            ? `/projects/${persisted.id}`
            : template === 'Blank'
              ? '/projects'
              : '/projects/templates',
          {
            method: persisted ? 'PUT' : 'POST',
            ...(persisted
              ? { headers: { 'If-Match': `"${persisted.version}"` } }
              : {}),
            body: JSON.stringify({
              ...persisted,
              template,
              language,
              name: name.trim(),
              description: description.trim(),
            }),
          },
        );
        setPersisted(saved);
        savedProject = saved;
      }
      if (!savedProject) throw new Error('Could not save project.');
      await onSaved(savedProject);
      onClose();
    } catch (cause) {
      setError(
        cause instanceof Error ? cause.message : 'Could not save project.',
      );
    } finally {
      setBusy(false);
    }
  }
  return (
    <Drawer
      title={t(project ? 'editProject' : 'newProject')}
      dirty={dirty}
      busy={busy}
      onClose={onClose}
    >
      <form className="editor-form" onSubmit={save}>
        <FormGuide topic="project" />
        <fieldset disabled={busy}>
          {!project && (
            <label>
              {t('template')}
              <select
                aria-label={t('template')}
                value={template}
                onChange={(event) => setTemplate(event.target.value)}
              >
                <option value="Blank">{t('blankProject')}</option>
                <option value="Scrum">Scrum</option>
                <option value="Kanban">Kanban</option>
              </select>
              <small>
                {t(
                  template === 'Scrum'
                    ? 'scrumHelp'
                    : template === 'Kanban'
                      ? 'kanbanHelp'
                      : 'blankHelp',
                )}
              </small>
            </label>
          )}
          <label>
            {t('projectName')}
            <input
              required
              maxLength={255}
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder={fieldExample('projectName')}
            />
          </label>
          <label>
            {t('descriptionLabel')}
            <textarea
              aria-label={t('descriptionLabel')}
              rows={5}
              placeholder={fieldExample('projectDescription')}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </label>
        </fieldset>
        <Notice error={error} />
        <div className="editor-footer">
          <button className="primary-action" disabled={busy}>
            {t(busy ? 'saving' : project ? 'saveProject' : 'createProject')}
          </button>
        </div>
      </form>
    </Drawer>
  );
}
