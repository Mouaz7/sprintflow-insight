import { useState, type FormEvent } from 'react';
import { api } from '../../api/client';
import type { Project, User, Language } from '../../api/contracts';
import { FormGuide, fieldExample } from '../../shared/ui/Guidance';
import { Drawer, Notice, PageHeader, useConfirm } from '../../shared/ui/ui';
import { InterfaceIcon } from '../../shared/ui/InterfaceIcons';
import { MemberManager } from './MemberManager';

export function ProjectsView({
  projects,
  project,
  user,
  language,
  onChanged,
  onSelect,
}: {
  projects: Project[];
  project?: Project;
  user: User;
  language: Language;
  onChanged: () => Promise<void>;
  onSelect: (id: number) => void;
}) {
  const sv = language === 'SV';
  const confirm = useConfirm();
  const [editor, setEditor] = useState<{ project?: Project }>();
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
      !(await confirm(
        sv
          ? `Flytta projektet “${item.name}” till papperskorgen? Allt innehåll följer med och kan återställas.`
          : `Move project “${item.name}” to the recycle bin? All its contents are retained and can be restored.`,
      ))
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
        language={language}
        eyebrow={sv ? 'PROJEKTREGISTER' : 'PROJECT DIRECTORY'}
        title={sv ? 'Projekt' : 'Projects'}
        description={
          sv
            ? 'Ett gemensamt hem för varje initiativ.'
            : 'A shared home for every initiative.'
        }
        action={
          <button className="primary-action" onClick={() => setEditor({})}>
            + {sv ? 'Skapa projekt' : 'Create project'}
          </button>
        }
      />
      <Notice language={language} error={error} />
      <div className="collection-actions">
        <button
          aria-pressed={!showArchived}
          onClick={() => setShowArchived(false)}
        >
          {sv ? 'Aktiva projekt' : 'Active projects'}
        </button>
        <button
          aria-pressed={showArchived}
          onClick={() => setShowArchived(true)}
        >
          {sv ? 'Arkiverade projekt' : 'Archived projects'}
        </button>
      </div>
      <div className="directory-toolbar">
        <input
          type="search"
          aria-label={sv ? 'Sök projekt' : 'Search projects'}
          placeholder={sv ? 'Sök projekt…' : 'Search projects…'}
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <span>
          {filtered.length}{' '}
          {sv ? 'projekt' : filtered.length === 1 ? 'project' : 'projects'}
        </span>
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
                {item.id === project?.id
                  ? sv
                    ? 'VALT PROJEKT'
                    : 'SELECTED PROJECT'
                  : sv
                    ? 'PROJEKT'
                    : 'PROJECT'}
              </span>
              <h2>{item.name}</h2>
              <p>
                {item.description ||
                  (sv ? 'Ingen beskrivning ännu.' : 'No description yet.')}
              </p>
              <small>
                {sv ? 'Ägare' : 'Owner'} · {item.ownerEmail}
              </small>
            </div>
            <div className="card-actions">
              <button onClick={() => archive(item)}>
                {item.archivedAt
                  ? sv
                    ? 'Återaktivera'
                    : 'Unarchive'
                  : sv
                    ? 'Arkivera'
                    : 'Archive'}
              </button>
              <button type="button" onClick={() => onSelect(item.id)}>
                {item.id === project?.id
                  ? sv
                    ? 'Valt'
                    : 'Selected'
                  : sv
                    ? 'Välj'
                    : 'Select'}
              </button>
              <button
                type="button"
                disabled={!!item.archivedAt}
                onClick={() => setEditor({ project: item })}
              >
                {sv ? 'Redigera' : 'Edit'}
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
                    ? sv
                      ? 'Endast projektägaren kan ta bort projektet.'
                      : 'Only the project owner can delete this project.'
                    : undefined
                }
                onClick={() => remove(item)}
              >
                {sv ? 'Ta bort' : 'Delete'}
              </button>
            </div>
          </article>
        ))}
      </div>
      {!filtered.length && (
        <div className="empty-state">
          <InterfaceIcon name="projects" />
          <h2>
            {showArchived
              ? sv
                ? 'Inga arkiverade projekt'
                : 'No archived projects'
              : sv
                ? 'Inga projekt att visa'
                : 'No projects to show'}
          </h2>
          <p>
            {query.trim()
              ? sv
                ? 'Prova ett annat sökord.'
                : 'Try another search.'
              : showArchived
                ? sv
                  ? 'Arkiverade projekt visas här.'
                  : 'Archived projects appear here.'
                : projects.some((item) => item.archivedAt)
                  ? sv
                    ? 'Alla projekt är arkiverade. Öppna Arkiverade projekt för att återaktivera dem.'
                    : 'All projects are archived. Open Archived projects to reactivate them.'
                  : sv
                    ? 'Skapa ditt första projekt för att komma igång.'
                    : 'Create your first project to get started.'}
          </p>
        </div>
      )}
      {project && (
        <section className="selected-project-details">
          <header>
            <span className="eyebrow">
              {sv ? 'PROJEKTDETALJER' : 'PROJECT DETAILS'}
            </span>
            <h2>{sv ? 'Projektinformation' : 'Project information'}</h2>
            <p>{project.description}</p>
          </header>
          <MemberManager
            project={project}
            userEmail={user.email}
            language={language}
            onChanged={onChanged}
          />
        </section>
      )}
      {editor && (
        <ProjectEditor
          key={editor.project?.id ?? 'new'}
          project={editor.project}
          language={language}
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
  language,
  onClose,
  onSaved,
}: {
  project?: Project;
  language: Language;
  onClose: () => void;
  onSaved: (saved: Project) => Promise<void>;
}) {
  const sv = language === 'SV';
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
      setError(sv ? 'Ange ett projektnamn.' : 'Enter a project name.');
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
            headers: persisted
              ? { 'If-Match': `"${persisted.version}"` }
              : undefined,
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
      title={
        project
          ? sv
            ? 'Redigera projekt'
            : 'Edit project'
          : sv
            ? 'Nytt projekt'
            : 'New project'
      }
      dirty={dirty}
      busy={busy}
      language={language}
      onClose={onClose}
    >
      <form className="editor-form" onSubmit={save}>
        <FormGuide topic="project" language={language} />
        <fieldset disabled={busy}>
          {!project && (
            <label>
              {sv ? 'Projektmall' : 'Project template'}
              <select
                aria-label={sv ? 'Projektmall' : 'Project template'}
                value={template}
                onChange={(event) => setTemplate(event.target.value)}
              >
                <option value="Blank">
                  {sv ? 'Tomt projekt' : 'Blank project'}
                </option>
                <option value="Scrum">Scrum</option>
                <option value="Kanban">Kanban</option>
              </select>
              <small>
                {template === 'Scrum'
                  ? sv
                    ? 'Skapar en planerad sprint, en berättelse och tre startuppgifter.'
                    : 'Creates a planned sprint, one story and three starter tasks.'
                  : template === 'Kanban'
                    ? sv
                      ? 'Skapar tre uppgifter för att starta ett löpande arbetsflöde.'
                      : 'Creates three starter tasks for continuous work.'
                    : sv
                      ? 'Du skapar allt innehåll själv.'
                      : 'Start without generated content.'}
              </small>
            </label>
          )}
          <label>
            {sv ? 'Projektnamn' : 'Project name'}
            <input
              required
              maxLength={255}
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder={fieldExample('projectName', language)}
            />
          </label>
          <label>
            {sv ? 'Beskrivning' : 'Description'}
            <textarea
              aria-label={sv ? 'Beskrivning' : 'Description'}
              rows={5}
              placeholder={fieldExample('projectDescription', language)}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </label>
        </fieldset>
        <Notice language={language} error={error} />
        <div className="editor-footer">
          <button className="primary-action" disabled={busy}>
            {busy
              ? sv
                ? 'Sparar…'
                : 'Saving…'
              : project
                ? sv
                  ? 'Spara projekt'
                  : 'Save project'
                : sv
                  ? 'Skapa projekt'
                  : 'Create project'}
          </button>
        </div>
      </form>
    </Drawer>
  );
}
