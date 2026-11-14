import { FormGuide, fieldExample } from './Guidance';
import { useDraftProject } from './useDraftProject';
import { localizeMessage } from './messages';
import { FormEvent, useEffect, useState } from 'react';
import { api } from '../api/client';
import type { Story, Sprint } from '../api/contracts';
import { Drawer, useConfirm } from './ui';

export function BacklogManager({
  projectId,
  items,
  language,
  onChanged,
}: {
  projectId: number;
  items: Story[];
  language: 'EN' | 'SV';
  onChanged: (saved?: Story) => void;
}) {
  const confirm = useConfirm();
  const sv = language === 'SV';
  const [panelOpen, setPanelOpen] = useState(false);
  const draft = useDraftProject(projectId, language);
  const [editing, setEditing] = useState<Story>();
  const [sprints, setSprints] = useState<Sprint[]>([]);
  const [sprintLoading, setSprintLoading] = useState(true);
  const [sprintError, setSprintError] = useState('');
  const sprintProjectId = panelOpen && !editing ? draft.id : projectId;
  useEffect(() => {
    let live = true;
    setSprints([]);
    setSprintLoading(true);
    setSprintError('');
    api<Sprint[]>(`/sprints?projectId=${sprintProjectId}`)
      .then(items => { if (live) setSprints(items); })
      .catch((cause: Error) => { if (live) setSprintError(cause.message); })
      .finally(() => { if (live) setSprintLoading(false); });
    return () => { live = false; };
  }, [sprintProjectId]);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [criteria, setCriteria] = useState('');
  const [points, setPoints] = useState(1);
  const [priority, setPriority] = useState(2);
  const [businessValue, setBusinessValue] = useState(3);
  const [sprintId, setSprintId] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [savedTitle, setSavedTitle] = useState('');
  function begin(story?: Story) {
    draft.reset();
    setSavedTitle('');
    setPanelOpen(true);
    setEditing(story);
    setTitle(story?.title ?? '');
    setDescription(story?.description ?? '');
    setCriteria(story?.acceptanceCriteria ?? '');
    setPoints(story?.storyPoints ?? 1);
    setPriority(story?.priorityId ?? 2);
    setBusinessValue(story?.businessValue ?? 3);
    setSprintId(story?.sprintId ? String(story.sprintId) : '');
    setError('');
  }
  async function save(event: FormEvent) {
    event.preventDefault();
    if (saving || sprintLoading || sprintError || (!editing && draft.blocked)) return;
    setSaving(true);
    setError('');
    const body = {
      ...(editing ?? {}),
      title,
      description,
      acceptanceCriteria: criteria,
      storyPoints: points,
      priorityId: priority,
      businessValue,
      sprintId: sprintId ? Number(sprintId) : null,
      projectId: editing ? projectId : draft.id,
    };
    try {
      const saved = await api<Story>(editing ? `/stories/${editing.id}` : '/stories', {
        method: editing ? 'PUT' : 'POST',
        headers: editing ? { 'If-Match': `"${editing.version}"` } : undefined,
        body: JSON.stringify(body),
      });
      if (!draft.reveal(saved.projectId)) onChanged(saved);
      setSavedTitle(saved.title);
      setSearch('');
      setSprintFilter('all');
      setPanelOpen(false);
    } catch (cause) {
      setError(
        cause instanceof Error ? cause.message : 'Could not save the story.',
      );
    } finally {
      setSaving(false);
    }
  }
  async function remove(story: Story) {
    if (
      !(await confirm(
        sv
          ? `Ta bort “${story.title}”?`
          : `Move “${story.title}” to the recycle bin?`,
      ))
    )
      return;
    try {
      await api<void>(`/workspace/trash/story/${story.id}`, {
        method: 'PUT',
        headers: { 'If-Match': `"${story.version}"` },
        body: JSON.stringify({ restore: false }),
      });
      onChanged();
    } catch (cause) {
      setError(
        cause instanceof Error ? cause.message : 'Could not delete this story.',
      );
    }
  }
  const [search, setSearch] = useState('');
  const [sprintFilter, setSprintFilter] = useState('all');
  const [sortBy, setSortBy] = useState<
    'manual' | 'priority' | 'title' | 'points'
  >('manual');
  const visibleStories = items
    .filter(
      (story) =>
        sprintFilter === 'all' ||
        (sprintFilter === 'backlog'
          ? story.sprintId == null
          : String(story.sprintId) === sprintFilter),
    )
    .filter((story) =>
      `${story.title} ${story.description ?? ''} ${story.acceptanceCriteria}`
        .toLowerCase()
        .includes(search.toLowerCase()),
    )
    .slice()
    .sort((a, b) =>
      sortBy === 'manual'
        ? (a.backlogPosition ?? a.id) - (b.backlogPosition ?? b.id) ||
          a.id - b.id
        : sortBy === 'title'
          ? a.title.localeCompare(b.title)
          : sortBy === 'points'
            ? b.storyPoints - a.storyPoints
            : a.priorityId - b.priorityId,
    );
  async function move(story: Story, offset: number) {
    if (saving) return;
    const target = visibleStories[visibleStories.indexOf(story) + offset];
    if (!target) return;
    const ordered = [...items].sort(
      (a, b) =>
        (a.backlogPosition ?? a.id) - (b.backlogPosition ?? b.id) ||
        a.id - b.id,
    );
    const from = ordered.findIndex((item) => item.id === story.id);
    const to = ordered.findIndex((item) => item.id === target.id);
    ordered.splice(from, 1);
    ordered.splice(to, 0, story);
    setSaving(true);
    setError('');
    try {
      await api(`/stories/project/${projectId}/order`, {
        method: 'PUT',
        body: JSON.stringify({
          items: ordered.map(({ id, version }) => ({ id, version })),
        }),
      });
      onChanged();
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : 'Could not save backlog order.',
      );
    } finally {
      setSaving(false);
    }
  }
  return (
    <div className="manager-layout">
      <div className="collection-actions">
        <span>
          {items.length}{' '}
          {sv
            ? items.length === 1
              ? 'berättelse'
              : 'berättelser'
            : items.length === 1
              ? 'story'
              : 'stories'}
        </span>
        <button className="primary-action" onClick={() => begin()}>
          + {sv ? 'Ny berättelse' : 'New story'}
        </button>
      </div>
      {!panelOpen && savedTitle && (
        <p className="form-message success" role="status">
          {sv ? `Berättelsen ”${savedTitle}” har sparats.` : `Story “${savedTitle}” saved.`}
        </p>
      )}
      {!panelOpen && error && (
        <p className="form-message error" role="alert">
          {localizeMessage(error, sv ? 'SV' : 'EN')}
        </p>
      )}
      {panelOpen && (
        <Drawer
          title={
            editing
              ? sv
                ? 'Redigera berättelse'
                : 'Edit story'
              : sv
                ? 'Ny berättelse'
                : 'New story'
          }
          language={language}
          projectSelection={!editing && draft.projects ? {
            projects: draft.projects, value: draft.id, disabled: saving,
            onChange: (id) => {
              draft.change(id);
              setError('');
              setSprintId('');
            },
          } : undefined}
          onClose={() => setPanelOpen(false)}
          busy={saving}
          dirty={
            (!editing && draft.id !== projectId) ||
            title !== (editing?.title ?? '') ||
            description !== (editing?.description ?? '') ||
            criteria !== (editing?.acceptanceCriteria ?? '') ||
            points !== (editing?.storyPoints ?? 1) ||
            priority !== (editing?.priorityId ?? 2) ||
            businessValue !== (editing?.businessValue ?? 3) ||
            sprintId !== (editing?.sprintId ? String(editing.sprintId) : '')
          }
        >
          <form className="editor-form" onSubmit={save}>
            <FormGuide topic="story" language={language} />
            <fieldset disabled={saving}>
              <label>
                {sv ? 'Titel' : 'Title'}
                <input
                  required
                  maxLength={255}
                  placeholder={fieldExample('storyTitle', language)}
                  value={title}
                  onChange={(event) => setTitle(event.target.value)}
                />
              </label>
              <label>
                {sv ? 'Beskrivning' : 'Description'}
                <textarea
                  aria-label={sv ? 'Beskrivning' : 'Description'}
                  rows={3}
                  placeholder={fieldExample('storyDescription', language)}
                  value={description}
                  onChange={(event) => setDescription(event.target.value)}
                />
              </label>
              <label>
                {sv ? 'Acceptanskriterier' : 'Acceptance criteria'}
                <textarea
                  aria-label={sv ? 'Acceptanskriterier' : 'Acceptance criteria'}
                  required
                  rows={3}
                  placeholder={fieldExample('criteria', language)}
                  value={criteria}
                  onChange={(event) => setCriteria(event.target.value)}
                />
              </label>
              <div className="form-row">
                <label>
                  {sv ? 'Poäng' : 'Story points'}
                  <select
                    aria-label={sv ? 'Poäng' : 'Story points'}
                    value={points}
                    onChange={(event) => setPoints(Number(event.target.value))}
                  >
                    {[1, 2, 3, 5, 8].map((value) => (
                      <option key={value} value={value}>
                        {value}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  {sv ? 'Prioritet' : 'Priority'}
                  <select
                    aria-label={sv ? 'Prioritet' : 'Priority'}
                    value={priority}
                    onChange={(event) =>
                      setPriority(Number(event.target.value))
                    }
                  >
                    <option value={1}>{sv ? 'Hög' : 'High'}</option>
                    <option value={2}>{sv ? 'Medel' : 'Medium'}</option>
                    <option value={3}>{sv ? 'Låg' : 'Low'}</option>
                  </select>
                </label>
                <label>
                  {sv ? 'Affärsvärde (1–5)' : 'Business value (1–5)'}
                  <select
                    aria-label={
                      sv ? 'Affärsvärde (1–5)' : 'Business value (1–5)'
                    }
                    value={businessValue}
                    onChange={(event) =>
                      setBusinessValue(Number(event.target.value))
                    }
                  >
                    {[1, 2, 3, 4, 5].map((value) => (
                      <option key={value} value={value}>
                        {value}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  {sv ? 'Sprint' : 'Sprint'}
                  <select
                    aria-label={sv ? 'Sprint' : 'Sprint'}
                    value={sprintId}
                    onChange={(event) => setSprintId(event.target.value)}
                  >
                    <option value="">
                      {sv ? 'Produktbacklog' : 'Product backlog'}
                    </option>
                    {sprints.map((sprint) => (
                      <option value={sprint.id} key={sprint.id}>
                        {sprint.name}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
            </fieldset>
            {!editing && draft.error && <p className="form-message error" role="alert">{localizeMessage(draft.error, language)}</p>}
            {sprintError && <p className="form-message error" role="alert">{localizeMessage(sprintError, language)}</p>}
            {error && (
              <p className="form-message error" role="alert">
                {localizeMessage(error, sv ? 'SV' : 'EN')}
              </p>
            )}
            <div className="form-actions">
              <button className="primary-action" disabled={saving || sprintLoading || !!sprintError || (!editing && draft.blocked)}>
                {saving
                  ? sv
                    ? 'Sparar…'
                    : 'Saving…'
                  : editing
                    ? sv
                      ? 'Spara ändringar'
                      : 'Save changes'
                    : sv
                      ? 'Skapa berättelse'
                      : 'Create story'}
              </button>
            </div>
          </form>
        </Drawer>
      )}
      <div className="backlog-toolbar">
        <input
          type="search"
          aria-label={sv ? 'Sök berättelser' : 'Search stories'}
          placeholder={sv ? 'Sök i backloggen…' : 'Search backlog…'}
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
        <select
          aria-label={
            sv ? 'Filtrera backlog efter sprint' : 'Filter backlog by sprint'
          }
          value={sprintFilter}
          onChange={(event) => setSprintFilter(event.target.value)}
        >
          <option value="all">{sv ? 'Alla sprintar' : 'All sprints'}</option>
          <option value="backlog">
            {sv ? 'Produktbacklog' : 'Product backlog'}
          </option>
          {sprints.map((sprint) => (
            <option key={sprint.id} value={sprint.id}>
              {sprint.name}
            </option>
          ))}
        </select>
        <select
          aria-label={sv ? 'Sortera backlog' : 'Sort backlog'}
          value={sortBy}
          onChange={(event) => setSortBy(event.target.value as typeof sortBy)}
        >
          <option value="manual">
            {sv ? 'Manuell ordning' : 'Manual order'}
          </option>
          <option value="priority">{sv ? 'Prioritet' : 'Priority'}</option>
          <option value="title">{sv ? 'Titel' : 'Title'}</option>
          <option value="points">Story points</option>
        </select>
      </div>
      {visibleStories.length ? (
        <table className="backlog-table">
          <caption className="sr-only">
            {sv ? 'Produktbacklog' : 'Product backlog'}
          </caption>
          <thead>
            <tr>
              <th>{sv ? 'Berättelse' : 'Story'}</th>
              <th>{sv ? 'Prioritet' : 'Priority'}</th>
              <th>{sv ? 'Poäng' : 'Points'}</th>
              <th>{sv ? 'Affärsvärde' : 'Value'}</th>
              <th>Sprint</th>
              <th>
                <span className="sr-only">{sv ? 'Åtgärder' : 'Actions'}</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {visibleStories.map((story) => (
              <tr key={story.id}>
                <td data-label={sv ? 'Berättelse' : 'Story'}>
                  <h2>{story.title}</h2>
                  <p>
                    {story.description ||
                      (sv ? 'Ingen beskrivning.' : 'No description.')}
                  </p>
                  <details>
                    <summary>
                      {sv ? 'Acceptanskriterier' : 'Acceptance criteria'}
                    </summary>
                    <p>{story.acceptanceCriteria}</p>
                  </details>
                </td>
                <td data-label={sv ? 'Prioritet' : 'Priority'}>
                  <span className={'status-badge priority-' + story.priorityId}>
                    {story.priorityId === 1
                      ? sv
                        ? 'Hög'
                        : 'High'
                      : story.priorityId === 2
                        ? sv
                          ? 'Medel'
                          : 'Medium'
                        : sv
                          ? 'Låg'
                          : 'Low'}
                  </span>
                </td>
                <td data-label={sv ? 'Poäng' : 'Points'}>
                  {story.storyPoints}
                </td>
                <td data-label={sv ? 'Affärsvärde' : 'Value'}>
                  {story.businessValue ?? '—'}
                </td>
                <td data-label="Sprint">
                  {sprints.find((sprint) => sprint.id === story.sprintId)
                    ?.name ?? (sv ? 'Ej planerad' : 'Unplanned')}
                </td>
                <td>
                  <div className="card-actions">
                    {sortBy === 'manual' && (
                      <>
                        <button
                          type="button"
                          disabled={saving || story === visibleStories[0]}
                          aria-label={`${sv ? 'Flytta upp' : 'Move up'}: ${story.title}`}
                          onClick={() => move(story, -1)}
                        >
                          ↑
                        </button>
                        <button
                          type="button"
                          disabled={
                            saving ||
                            story === visibleStories[visibleStories.length - 1]
                          }
                          aria-label={`${sv ? 'Flytta ned' : 'Move down'}: ${story.title}`}
                          onClick={() => move(story, 1)}
                        >
                          ↓
                        </button>
                      </>
                    )}
                    <button type="button" onClick={() => begin(story)}>
                      {sv ? 'Redigera' : 'Edit'}
                    </button>
                    <button
                      type="button"
                      className="danger-action"
                      onClick={() => remove(story)}
                    >
                      {sv ? 'Ta bort' : 'Delete'}
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        <div className="empty-state">
          {items.length
            ? sv
              ? 'Inga berättelser matchar din sökning.'
              : 'No stories match your search.'
            : sv
              ? 'Backloggen är tom. Skapa den första berättelsen.'
              : 'The backlog is empty. Create the first story.'}
        </div>
      )}
    </div>
  );
}
