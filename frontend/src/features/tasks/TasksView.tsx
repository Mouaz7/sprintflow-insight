import { FormGuide, fieldExample } from '../../shared/ui/Guidance';
import { useDraftProject } from '../../shared/lib/useDraftProject';
import { DateInput } from '../../shared/ui/DateInput';
import {
  MIN_DATE,
  MAX_DATE,
  DATE_ERROR,
  validDate,
  normalizePoints,
  validPoints,
} from '../../shared/lib/formValues';
import { TaskAttachments } from './TaskAttachments';
import { BlockTaskPanel } from './BlockTaskPanel';
import { localizeMessage } from '../../shared/i18n/messages';
import { TaskMetadata, TaskDiscussion } from './TaskCollaboration';
import { SavedFilters } from './SavedFilters';
import { FormEvent, useEffect, useRef, useState } from 'react';
import { api } from '../../api/client';
import type { Task, Story, Sprint, ProjectMember } from '../../api/contracts';
import { Drawer, PageHeader, useConfirm } from '../../shared/ui/ui';

export function TasksView({
  hasProjects,
  projectId,
  language,
  openRequest = 0,
  onRequestHandled,
  userEmail = '',
  readOnly = false,
}: {
  userEmail?: string;
  readOnly?: boolean;
  hasProjects: boolean;
  projectId?: number;
  language: 'EN' | 'SV';
  openRequest?: number;
  onRequestHandled?: () => void;
}) {
  const confirm = useConfirm();
  const sv = language === 'SV';
  const draft = useDraftProject(projectId, language);
  const [panelOpen, setPanelOpen] = useState(false);
  const [savedTitle, setSavedTitle] = useState('');
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    if (openRequest) {
      setPanelOpen(true);
      onRequestHandled?.();
    }
  }, [openRequest, onRequestHandled]);
  const [tasks, setTasks] = useState<Task[]>([]);
  const [stories, setStories] = useState<Story[]>([]);
  const [sprints, setSprints] = useState<Sprint[]>([]);
  const [members, setMembers] = useState<ProjectMember[]>([]);
  const [projectRole, setProjectRole] = useState('');
  const [blocking, setBlocking] = useState<Task>();
  const [discussion, setDiscussion] = useState<Task>();
  const role =
    projectRole ||
    members.find(
      (member) => member.email.toLowerCase() === userEmail.toLowerCase(),
    )?.role;
  const canWrite = !readOnly && role !== 'Viewer';
  const [editing, setEditing] = useState<Task>();
  const foreignDraft = panelOpen && !editing && draft.id !== projectId;
  const [draftLinks, setDraftLinks] = useState<{ id?: number; stories: Story[]; sprints: Sprint[]; members: ProjectMember[] }>();
  const [draftLinksError, setDraftLinksError] = useState('');
  useEffect(() => {
    if (!foreignDraft || !draft.id) return;
    let live = true;
    setDraftLinks(undefined);
    setDraftLinksError('');
    Promise.all([
      api<Story[]>(`/stories/project/${draft.id}`),
      api<Sprint[]>(`/sprints?projectId=${draft.id}`),
      api<ProjectMember[]>(`/projects/${draft.id}/members`),
    ]).then(([stories, sprints, members]) => {
      if (live) setDraftLinks({ id: draft.id, stories, sprints, members });
    }).catch((cause: Error) => { if (live) setDraftLinksError(cause.message); });
    return () => { live = false; };
  }, [foreignDraft, draft.id]);
  const draftLinksLoading = foreignDraft && draftLinks?.id !== draft.id;
  const editorStories = foreignDraft ? draftLinks && draftLinks.id === draft.id ? draftLinks.stories : [] : stories;
  const editorSprints = foreignDraft ? draftLinks && draftLinks.id === draft.id ? draftLinks.sprints : [] : sprints;
  const editorMembers = foreignDraft ? draftLinks && draftLinks.id === draft.id ? draftLinks.members : [] : members;
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [points, setPoints] = useState('0');
  const [dueDate, setDueDate] = useState('');
  const [storyId, setStoryId] = useState('');
  const [sprintId, setSprintId] = useState('');
  const [assigneeEmail, setAssigneeEmail] = useState('');
  const [query, setQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [sprintFilter, setSprintFilter] = useState('');
  const [assigneeFilter, setAssigneeFilter] = useState('');
  const [dueFilter, setDueFilter] = useState('');
  const [blockedOnly, setBlockedOnly] = useState(false);
  const [labelFilter, setLabelFilter] = useState('');
  const [draggedTaskId, setDraggedTaskId] = useState<number>();
  const [message, setMessage] = useState('');
  const [saving, setSaving] = useState(false);
  const pendingTasks = useRef(new Set<number>());
  const [busyTasks, setBusyTasks] = useState(new Set<number>());
  function beginTaskChange(id: number): boolean {
    if (pendingTasks.current.has(id)) return false;
    pendingTasks.current.add(id);
    setBusyTasks(new Set(pendingTasks.current));
    return true;
  }
  function endTaskChange(id: number) {
    pendingTasks.current.delete(id);
    setBusyTasks(new Set(pendingTasks.current));
  }

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setTasks([]);
    setStories([]);
    setSprints([]);
    setMembers([]);
    setProjectRole('');
    setMessage('');
    if (!projectId) {
      setLoading(false);
      return () => {
        cancelled = true;
      };
    }
    Promise.all([
      api<Task[]>(`/tasks?projectId=${projectId}`),
      api<Story[]>(`/stories/project/${projectId}`),
      api<Sprint[]>(`/sprints?projectId=${projectId}`),
      api<ProjectMember[]>(`/projects/${projectId}/members`),
      api<{ role: string }>(`/projects/${projectId}/role`),
    ])
      .then(([taskItems, storyItems, sprintItems, memberItems, roleValue]) => {
        if (!cancelled) {
          setTasks(taskItems);
          setStories(storyItems);
          setSprints(sprintItems);
          setMembers(memberItems);
          setProjectRole(roleValue?.role ?? '');
        }
      })
      .catch((cause: Error) => {
        if (!cancelled) setMessage(cause.message);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [projectId]);
  function clearForm() {
    draft.reset();
    setSavedTitle('');
    setPanelOpen(false);
    setEditing(undefined);
    setTitle('');
    setDescription('');
    setPoints('0');
    setDueDate('');
    setStoryId('');
    setSprintId('');
    setAssigneeEmail('');
  }
  function editTask(task: Task) {
    setSavedTitle('');
    if (pendingTasks.current.has(task.id)) return;
    setPanelOpen(true);
    setEditing(task);
    setTitle(task.title);
    setDescription(task.description ?? '');
    setPoints(String(task.storyPoints));
    setDueDate(
      task.dueAt ? new Date(task.dueAt).toLocaleDateString('sv-SE') : '',
    );
    setStoryId(task.storyId ? String(task.storyId) : '');
    setSprintId(task.sprintId ? String(task.sprintId) : '');
    setAssigneeEmail(task.assigneeEmail ?? '');
  }
  async function saveTask(event: FormEvent) {
    event.preventDefault();
    if (!projectId || saving || (!editing && (draft.blocked || draftLinksLoading || (foreignDraft && draftLinksError)))) return;
    if (!validPoints(points, 1000)) {
      setMessage(
        sv
          ? 'Ange ett heltal mellan 0 och 1000.'
          : 'Enter a whole number between 0 and 1000.',
      );
      return;
    }
    if (dueDate && !validDate(dueDate)) {
      setMessage(localizeMessage(DATE_ERROR, sv ? 'SV' : 'EN'));
      return;
    }
    setSaving(true);
    setMessage('');
    const body = {
      ...(editing ?? {}),
      title,
      description,
      projectId: editing ? projectId : draft.id,
      status: editing?.status ?? 'To Do',
      storyPoints: Number(points),
      dueAt: dueDate ? new Date(`${dueDate}T23:59:59`).toISOString() : null,
      storyId: storyId ? Number(storyId) : null,
      sprintId: sprintId ? Number(sprintId) : null,
      assigneeEmail: assigneeEmail || null,
      blocked: editing?.blocked ?? false,
      blockReason: editing?.blockReason ?? null,
    };
    try {
      const saved = await api<Task>(
        editing ? `/tasks/${editing.id}` : '/tasks',
        {
          method: editing ? 'PUT' : 'POST',
          headers: editing ? { 'If-Match': `"${editing.version}"` } : undefined,
          body: JSON.stringify(body),
        },
      );
      if (!draft.reveal(saved.projectId)) setTasks((current) =>
        editing
          ? current.map((task) => (task.id === saved.id ? saved : task))
          : [...current, saved],
      );
      clearForm();
      setQuery('');
      setStatusFilter('');
      setSprintFilter('');
      setAssigneeFilter('');
      setDueFilter('');
      setBlockedOnly(false);
      setLabelFilter('');
      setSavedTitle(saved.title);
    } catch (cause) {
      setMessage(
        cause instanceof Error ? cause.message : 'Could not save task.',
      );
    } finally {
      setSaving(false);
    }
  }
  async function updateTask(
    task: Task,
    changes: Partial<Task>,
  ): Promise<boolean> {
    if (!beginTaskChange(task.id)) return false;
    setMessage('');
    try {
      const saved = await api<Task>(`/tasks/${task.id}`, {
        method: 'PUT',
        headers: { 'If-Match': `"${task.version}"` },
        body: JSON.stringify({ ...task, ...changes, projectId }),
      });
      setTasks((current) =>
        current.map((item) => (item.id === saved.id ? saved : item)),
      );
      return true;
    } catch (cause) {
      setMessage(
        cause instanceof Error ? cause.message : 'Could not update task.',
      );
      return false;
    } finally {
      endTaskChange(task.id);
    }
  }
  async function toggleBlocked(task: Task) {
    if (!projectId || pendingTasks.current.has(task.id)) return;
    if (task.blocked) {
      if (!beginTaskChange(task.id)) return;
      try {
        const saved = await api<Task>(`/tasks/${task.id}/unblock`, {
          method: 'PUT',
          headers: { 'If-Match': `"${task.version}"` },
        });
        setTasks((items) =>
          items.map((item) => (item.id === saved.id ? saved : item)),
        );
      } catch (cause) {
        setMessage(
          cause instanceof Error ? cause.message : 'Could not unblock task.',
        );
      } finally {
        endTaskChange(task.id);
      }
      return;
    }
    setBlocking(task);
  }
  async function deleteTask(task: Task) {
    if (pendingTasks.current.has(task.id)) return;
    if (
      !(await confirm(
        sv
          ? `Flytta “${task.title}” till papperskorgen? Du kan återställa den senare.`
          : `Move “${task.title}” to the recycle bin? You can restore it later.`,
      ))
    )
      return;
    if (!beginTaskChange(task.id)) return;
    try {
      await api<void>(`/workspace/trash/task/${task.id}`, {
        method: 'PUT',
        headers: { 'If-Match': `"${task.version}"` },
        body: JSON.stringify({ restore: false }),
      });
      setTasks((items) => items.filter((item) => item.id !== task.id));
    } catch (cause) {
      setMessage(
        cause instanceof Error ? cause.message : 'Could not delete task.',
      );
    } finally {
      endTaskChange(task.id);
    }
  }
  const columns = ['To Do', 'In Progress', 'Review', 'Done'] as const;
  const labels: Record<(typeof columns)[number], string> = sv
    ? {
        'To Do': 'Att göra',
        'In Progress': 'Pågår',
        Review: 'Granskning',
        Done: 'Klart',
      }
    : {
        'To Do': 'To Do',
        'In Progress': 'In Progress',
        Review: 'Review',
        Done: 'Done',
      };
  const filtered = tasks.filter(
    (task) =>
      (!statusFilter || task.status === statusFilter) &&
      (!sprintFilter ||
        (sprintFilter === 'unassigned'
          ? !task.sprintId
          : String(task.sprintId ?? '') === sprintFilter)) &&
      (!assigneeFilter ||
        (assigneeFilter === 'unassigned'
          ? !task.assigneeEmail
          : task.assigneeEmail === assigneeFilter)) &&
      (!dueFilter ||
        (dueFilter === 'overdue'
          ? Boolean(
              task.dueAt &&
              new Date(task.dueAt) < new Date() &&
              task.status !== 'Done',
            )
          : dueFilter === 'has-date'
            ? Boolean(task.dueAt)
            : !task.dueAt)) &&
      (!blockedOnly || task.blocked) &&
      (!labelFilter || task.labels?.includes(labelFilter)) &&
      `${task.title} ${task.description ?? ''}`
        .toLowerCase()
        .includes(query.toLowerCase()),
  );
  const exportParams = new URLSearchParams();
  for (const [key, value] of Object.entries({
    status: statusFilter,
    assignee: assigneeFilter,
    due: dueFilter,
    label: labelFilter,
    q: query,
    blocked: blockedOnly ? 'true' : '',
  }))
    if (value) exportParams.set(key, value);
  if (sprintFilter === 'unassigned') exportParams.set('sprint', 'backlog');
  else if (sprintFilter) exportParams.set('sprintId', sprintFilter);
  const exportQuery = exportParams.size ? `?${exportParams}` : '';
  return (
    <section className="page tasks-page">
      <PageHeader
        guide="tasks"
        language={language}
        eyebrow={sv ? 'TEAMETS ARBETE' : 'TEAM EXECUTION'}
        title={sv ? 'Uppgifter' : 'Tasks'}
        description={
          sv
            ? 'Från nästa steg till levererat arbete.'
            : 'From the next step to delivered work.'
        }
        action={
          projectId && (
            <button
              className="primary-action"
              disabled={!canWrite}
              onClick={() => {
                clearForm();
                setMessage('');
                setPanelOpen(true);
              }}
            >
              + {sv ? 'Ny uppgift' : 'New task'}
            </button>
          )
        }
      />
      {hasProjects && projectId ? (
        <>
          {blocking && (
            <BlockTaskPanel
              task={blocking}
              language={language}
              onClose={() => setBlocking(undefined)}
              onSaved={(saved) => {
                setTasks((items) =>
                  items.map((item) => (item.id === saved.id ? saved : item)),
                );
                setBlocking(undefined);
              }}
            />
          )}
          {panelOpen && (
            <Drawer
              title={
                editing
                  ? sv
                    ? 'Redigera uppgift'
                    : 'Edit task'
                  : sv
                    ? 'Ny uppgift'
                    : 'New task'
              }
              language={language}
              projectSelection={!editing && draft.projects ? {
                projects: draft.projects, value: draft.id, disabled: saving,
                onChange: (id) => {
                  draft.change(id);
                  setStoryId('');
                  setSprintId('');
                  setAssigneeEmail('');
                  setMessage('');
                },
              } : undefined}
              onClose={clearForm}
              busy={saving}
              dirty={
                (!editing && draft.id !== projectId) ||
                title !== (editing?.title ?? '') ||
                description !== (editing?.description ?? '') ||
                points !== String(editing?.storyPoints ?? 0) ||
                dueDate !==
                  (editing?.dueAt
                    ? new Date(editing.dueAt).toLocaleDateString('sv-SE')
                    : '') ||
                storyId !== (editing?.storyId ? String(editing.storyId) : '') ||
                sprintId !==
                  (editing?.sprintId ? String(editing.sprintId) : '') ||
                assigneeEmail !== (editing?.assigneeEmail ?? '')
              }
            >
              <form className="editor-form" onSubmit={saveTask}>
                <FormGuide topic="task" language={language} />
                <fieldset disabled={saving}>
                  <div className="task-form-grid">
                    <label>
                      {sv ? 'Titel' : 'Title'}
                      <input
                        required
                        maxLength={255}
                        value={title}
                        onChange={(event) => setTitle(event.target.value)}
                        placeholder={fieldExample('taskTitle', language)}
                      />
                    </label>
                    <label>
                      {sv ? 'Beskrivning' : 'Description'}
                      <input
                        placeholder={fieldExample('taskDescription', language)}
                        value={description}
                        onChange={(event) => setDescription(event.target.value)}
                      />
                    </label>
                    <label>
                      {sv ? 'Story points' : 'Story points'}
                      <input
                        type="number"
                        min={0}
                        max={1000}
                        step={1}
                        required
                        value={points}
                        onChange={(event) =>
                          setPoints(normalizePoints(event.target.value))
                        }
                      />
                    </label>
                    <label>
                      {sv ? 'Slutdatum' : 'Due date'}
                      <DateInput
                        language={language}
                        min={MIN_DATE}
                        max={MAX_DATE}
                        value={dueDate}
                        onBlur={() => {
                          if (dueDate && !validDate(dueDate))
                            setMessage(
                              localizeMessage(DATE_ERROR, sv ? 'SV' : 'EN'),
                            );
                        }}
                        onChange={(value) => {
                          setDueDate(value);
                          setMessage('');
                        }}
                      />
                    </label>
                    <label style={{ alignContent: 'start' }}>
                      {sv ? 'Berättelse' : 'User story'}
                      <select
                        aria-label={sv ? 'Berättelse' : 'User story'}
                        disabled={draftLinksLoading}
                        value={storyId}
                        onChange={(event) => {
                          const value = event.target.value;
                          setStoryId(value);
                          const story = editorStories.find(
                            (item) => String(item.id) === value,
                          );
                          setSprintId(
                            story?.sprintId ? String(story.sprintId) : '',
                          );
                        }}
                      >
                        <option value="">{sv ? 'Ingen' : 'None'}</option>
                        {editorStories.map((story) => (
                          <option value={story.id} key={story.id}>
                            {story.title}
                          </option>
                        ))}
                      </select>
                    </label>
                    <label>
                      {sv ? 'Sprint' : 'Sprint'}
                      <select
                        aria-label={sv ? 'Sprint' : 'Sprint'}
                        disabled={draftLinksLoading || !!storyId}
                        aria-describedby={
                          storyId ? 'task-sprint-inheritance' : undefined
                        }
                        value={sprintId}
                        onChange={(event) => setSprintId(event.target.value)}
                      >
                        <option value="">
                          {sv ? 'Produktbacklog' : 'Product backlog'}
                        </option>
                        {editorSprints.map((sprint) => (
                          <option value={sprint.id} key={sprint.id}>
                            {sprint.name}
                          </option>
                        ))}
                      </select>
                      {storyId && (
                        <small id="task-sprint-inheritance">
                          {sv
                            ? 'Följer berättelsens sprint, även i produktbackloggen.'
                            : 'Follows the story’s sprint, including the product backlog.'}
                        </small>
                      )}
                    </label>
                    <label>
                      {sv ? 'Ansvarig' : 'Assignee'}
                      <select
                        aria-label={sv ? 'Ansvarig' : 'Assignee'}
                        disabled={draftLinksLoading}
                        value={assigneeEmail}
                        onChange={(event) =>
                          setAssigneeEmail(event.target.value)
                        }
                      >
                        <option value="">{sv ? 'Ingen' : 'Unassigned'}</option>
                        {editorMembers.map((member) => (
                          <option key={member.id} value={member.email}>
                            {member.email}
                          </option>
                        ))}
                      </select>
                    </label>
                  </div>
                </fieldset>
                {!editing && draft.error && <p className="form-message error" role="alert">{localizeMessage(draft.error, language)}</p>}
                {foreignDraft && draftLinksError && <p className="form-message error" role="alert">{localizeMessage(draftLinksError, language)}</p>}
                {message && (
                  <p className="form-message error" role="alert">
                    {localizeMessage(message, sv ? 'SV' : 'EN')}
                  </p>
                )}
                <div className="form-actions">
                  <button className="primary-action" disabled={saving || (!editing && (draft.blocked || draftLinksLoading))}>
                    {saving
                      ? sv
                        ? 'Sparar…'
                        : 'Saving…'
                      : editing
                        ? sv
                          ? 'Spara ändringar'
                          : 'Save changes'
                        : sv
                          ? 'Skapa uppgift'
                          : 'Create task'}
                  </button>
                </div>
              </form>
            </Drawer>
          )}
          {!panelOpen && savedTitle && (
            <p className="form-message success" role="status">
              {sv ? `Uppgiften ”${savedTitle}” har sparats. Alla uppgifter visas.` : `Task “${savedTitle}” saved. Showing all tasks.`}
            </p>
          )}
          <SavedFilters
            projectId={projectId}
            userEmail={userEmail}
            language={language}
            current={{
              query,
              status: statusFilter,
              sprint: sprintFilter,
              assignee: assigneeFilter,
              due: dueFilter,
              blocked: blockedOnly,
              label: labelFilter,
            }}
            onApply={(value) => {
              setQuery(value.query);
              setStatusFilter(value.status);
              setSprintFilter(value.sprint);
              setAssigneeFilter(value.assignee);
              setDueFilter(value.due);
              setBlockedOnly(value.blocked);
              setLabelFilter(value.label);
            }}
          />
          <div className="task-toolbar task-filters">
            <select
              aria-label={sv ? 'Filtrera etikett' : 'Filter label'}
              value={labelFilter}
              onChange={(event) => setLabelFilter(event.target.value)}
            >
              <option value="">{sv ? 'Alla etiketter' : 'All labels'}</option>
              {[...new Set(tasks.flatMap((task) => task.labels ?? []))]
                .sort()
                .map((label) => (
                  <option key={label} value={label}>
                    {label}
                  </option>
                ))}
            </select>
            <input
              type="search"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder={sv ? 'Sök uppgifter…' : 'Search tasks…'}
              aria-label={sv ? 'Sök uppgifter' : 'Search tasks'}
            />
            <select
              value={statusFilter}
              onChange={(event) => setStatusFilter(event.target.value)}
              aria-label={sv ? 'Filtrera status' : 'Filter status'}
            >
              <option value="">{sv ? 'Alla statusar' : 'All statuses'}</option>
              {columns.map((column) => (
                <option key={column} value={column}>
                  {labels[column]}
                </option>
              ))}
            </select>
            <select
              value={sprintFilter}
              onChange={(event) => setSprintFilter(event.target.value)}
              aria-label={sv ? 'Filtrera sprint' : 'Filter sprint'}
            >
              <option value="">{sv ? 'Alla sprintar' : 'All sprints'}</option>
              <option value="unassigned">
                {sv ? 'Produktbacklogg' : 'Product backlog'}
              </option>
              {sprints.map((sprint) => (
                <option key={sprint.id} value={sprint.id}>
                  {sprint.name}
                </option>
              ))}
            </select>
            <select
              value={assigneeFilter}
              onChange={(event) => setAssigneeFilter(event.target.value)}
              aria-label={sv ? 'Filtrera ansvarig' : 'Filter assignee'}
            >
              <option value="">
                {sv ? 'Alla ansvariga' : 'All assignees'}
              </option>
              <option value="unassigned">
                {sv ? 'Ingen ansvarig' : 'Unassigned'}
              </option>
              {members.map((member) => (
                <option key={member.id} value={member.email}>
                  {member.email}
                </option>
              ))}
            </select>
            <select
              value={dueFilter}
              onChange={(event) => setDueFilter(event.target.value)}
              aria-label={sv ? 'Filtrera slutdatum' : 'Filter due date'}
            >
              <option value="">
                {sv ? 'Alla slutdatum' : 'All due dates'}
              </option>
              <option value="overdue">{sv ? 'Försenade' : 'Overdue'}</option>
              <option value="has-date">
                {sv ? 'Har slutdatum' : 'Has due date'}
              </option>
              <option value="no-date">
                {sv ? 'Inget slutdatum' : 'No due date'}
              </option>
            </select>
            <label className="blocked-filter">
              <input
                type="checkbox"
                checked={blockedOnly}
                onChange={(event) => setBlockedOnly(event.target.checked)}
              />
              {sv ? 'Blockerade' : 'Blocked'}
            </label>
          </div>
          <div className="export-actions">
            <a
              className="secondary-action"
              href={`/api/projects/${projectId}/export.csv${exportQuery}`}
            >
              {sv
                ? 'Exportera filtrerade uppgifter (CSV)'
                : 'Export filtered tasks (CSV)'}
            </a>
            <a
              className="secondary-action"
              href={`/api/projects/${projectId}/export.pdf${exportQuery}`}
            >
              {sv
                ? 'Exportera filtrerade uppgifter (PDF)'
                : 'Export filtered tasks (PDF)'}
            </a>
          </div>
          {!panelOpen && message && (
            <p className="form-message error" role="alert">
              {localizeMessage(message, sv ? 'SV' : 'EN')}
            </p>
          )}
          {loading && (
            <p role="status">{sv ? 'Laddar uppgifter…' : 'Loading tasks…'}</p>
          )}
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
                  <b>
                    {filtered.filter((task) => task.status === column).length}
                  </b>
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
                  <p className="empty-column">
                    {sv ? 'Inga uppgifter' : 'No tasks'}
                  </p>
                )}
              </section>
            ))}
          </div>
        </>
      ) : (
        <div className="empty-state">
          {sv
            ? 'Skapa ett projekt innan du lägger till uppgifter.'
            : 'Create a project before adding tasks.'}
        </div>
      )}
      {discussion && (
        <TaskDiscussion
          task={discussion}
          language={language}
          userEmail={userEmail}
          canModerate={role === 'Owner' || role === 'Admin'}
          readOnly={!canWrite}
          onClose={() => setDiscussion(undefined)}
        />
      )}
    </section>
  );
}
