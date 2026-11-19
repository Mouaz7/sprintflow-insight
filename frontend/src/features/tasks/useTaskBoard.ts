import { useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import { FormEvent, useEffect, useRef, useState } from 'react';
import { api } from '../../api/client';
import {
  appendCustomCondition,
  customMatchesQuery,
} from '../../api/customFields';
import type { Task, CustomFieldCondition } from '../../api/contracts';
import {
  afterProjectWrite,
  dependenciesQuery,
  membersQuery,
  roleQuery,
  sprintsQuery,
  storiesQuery,
  tasksQuery,
  wipLimitsQuery,
} from '../../api/queries';
import { localizeMessage } from '../../shared/i18n/messages';
import {
  DATE_ERROR,
  validDate,
  validPoints,
} from '../../shared/lib/formValues';
import { useDraftProject } from '../../shared/lib/useDraftProject';
import { useConfirm } from '../../shared/ui/ui';
import { useLanguage, useT } from '../../shared/i18n/LanguageProvider';
import { tasksText } from './i18n';
export type TasksViewProps = {
  userEmail?: string;
  readOnly?: boolean;
  hasProjects: boolean;
  projectId?: number | undefined;
  openRequest?: number;
  onRequestHandled?: () => void;
  /** Opens the board filtered to this task key, e.g. from search. */
  focusTaskKey?: string | undefined;
  focusRequestKey?: string | undefined;
};
export function useTaskBoard({
  hasProjects,
  projectId,
  openRequest = 0,
  onRequestHandled,
  userEmail = '',
  readOnly = false,
  focusTaskKey = '',
  focusRequestKey = '',
}: TasksViewProps) {
  const confirm = useConfirm();
  const { language } = useLanguage();
  const t = useT(tasksText);
  const draft = useDraftProject(projectId, language);
  const [panelOpen, setPanelOpen] = useState(false);
  const [savedTitle, setSavedTitle] = useState('');
  // A "new task" request from another view opens the editor once per request.
  const [handledRequest, setHandledRequest] = useState(0);
  if (openRequest !== handledRequest) {
    setHandledRequest(openRequest);
    if (openRequest) setPanelOpen(true);
  }
  useEffect(() => {
    if (openRequest) onRequestHandled?.();
  }, [openRequest, onRequestHandled]);
  const queryClient = useQueryClient();
  const board = useQueries({
    queries: [
      { ...tasksQuery(projectId ?? 0), enabled: !!projectId },
      { ...storiesQuery(projectId ?? 0), enabled: !!projectId },
      { ...sprintsQuery(projectId ?? 0), enabled: !!projectId },
      { ...membersQuery(projectId ?? 0), enabled: !!projectId },
      { ...roleQuery(projectId ?? 0), enabled: !!projectId },
      { ...dependenciesQuery(projectId ?? 0), enabled: !!projectId },
      { ...wipLimitsQuery(projectId ?? 0), enabled: !!projectId },
    ],
  });
  const [
    taskResult,
    storyResult,
    sprintResult,
    memberResult,
    roleResult,
    dependencyResult,
    wipResult,
  ] = board;
  // As before, one failed request leaves the whole board empty with its message.
  const loadError = board.find((result) => result.isError)?.error;
  const loaded = !!projectId && !loadError;
  const loading = !!projectId && board.some((result) => result.isPending);
  const tasks = (loaded && taskResult.data) || [];
  const stories = (loaded && storyResult.data) || [];
  const sprints = (loaded && sprintResult.data) || [];
  const members = (loaded && memberResult.data) || [];
  const projectRole = (loaded && roleResult.data?.role) || '';
  const dependencyBlocked = new Set(
    dependencyResult.data?.blockedTaskIds ?? [],
  );
  function setTasks(update: (items: Task[]) => Task[]) {
    if (!projectId) return;
    queryClient.setQueryData(tasksQuery(projectId).queryKey, (items) =>
      update(items ?? []),
    );
    // Other views of the project reload when they open.
    void afterProjectWrite(
      queryClient,
      projectId,
      dependenciesQuery(projectId).queryKey,
    );
  }
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
  // Choices for a new task saved into another project than the board's.
  const draftEnabled = foreignDraft && !!draft.id;
  const draftLinks = useQueries({
    queries: [
      { ...storiesQuery(draft.id ?? 0), enabled: draftEnabled },
      { ...sprintsQuery(draft.id ?? 0), enabled: draftEnabled },
      { ...membersQuery(draft.id ?? 0), enabled: draftEnabled },
    ],
  });
  const [draftStories, draftSprints, draftMembers] = draftLinks;
  const draftLinksError =
    (draftEnabled &&
      draftLinks.find((result) => result.isError)?.error?.message) ||
    '';
  const draftLinksReady =
    draftEnabled && draftLinks.every((result) => result.isSuccess);
  const draftLinksLoading = foreignDraft && !draftLinksReady;
  const editorStories = foreignDraft
    ? (draftLinksReady && draftStories.data) || []
    : stories;
  const editorSprints = foreignDraft
    ? (draftLinksReady && draftSprints.data) || []
    : sprints;
  const editorMembers = foreignDraft
    ? (draftLinksReady && draftMembers.data) || []
    : members;
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [points, setPoints] = useState('0');
  const [dueDate, setDueDate] = useState('');
  const [storyId, setStoryId] = useState('');
  const [sprintId, setSprintId] = useState('');
  const [assigneeEmail, setAssigneeEmail] = useState('');
  const focusSource = `${focusRequestKey}:${focusTaskKey}`;
  const [handledFocus, setHandledFocus] = useState(focusSource);
  const [query, setQuery] = useState(focusTaskKey);
  // A new navigation can target the same key after the user edited the filter.
  // Adjust the filter without remounting and losing an editor-open request.
  if (handledFocus !== focusSource) {
    setHandledFocus(focusSource);
    setQuery(focusTaskKey);
  }
  const [statusFilter, setStatusFilter] = useState('');
  const [sprintFilter, setSprintFilter] = useState('');
  const [assigneeFilter, setAssigneeFilter] = useState('');
  const [dueFilter, setDueFilter] = useState('');
  const [blockedOnly, setBlockedOnly] = useState(false);
  const [labelFilter, setLabelFilter] = useState('');
  const [customCondition, setCustomCondition] =
    useState<CustomFieldCondition | null>(null);
  const customMatches = useQuery(
    customMatchesQuery(projectId ?? 0, customCondition),
  );
  const matchedIds = new Set(
    customMatches.isError ? [] : (customMatches.data?.taskIds ?? []),
  );
  const [draggedTaskId, setDraggedTaskId] = useState<number>();
  // Undefined shows the load error, if any; actions set or clear their own.
  const [actionMessage, setMessage] = useState<string>();
  const message = actionMessage ?? loadError?.message ?? '';
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
    if (
      !projectId ||
      saving ||
      (!editing &&
        (draft.blocked ||
          draftLinksLoading ||
          (foreignDraft && draftLinksError)))
    )
      return;
    if (!validPoints(points, 1000)) {
      setMessage(t('pointsRange'));
      return;
    }
    if (dueDate && !validDate(dueDate)) {
      setMessage(localizeMessage(DATE_ERROR, language));
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
          ...(editing
            ? { headers: { 'If-Match': `"${editing.version}"` } }
            : {}),
          body: JSON.stringify(body),
        },
      );
      if (saved.projectId !== projectId)
        void afterProjectWrite(queryClient, saved.projectId);
      if (!draft.reveal(saved.projectId))
        setTasks((current) =>
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
    if (!(await confirm(t('confirmTrash', { title: task.title })))) return;
    if (!beginTaskChange(task.id)) return;
    try {
      await api<unknown>(`/workspace/trash/task/${task.id}`, {
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
  const labels: Record<(typeof columns)[number], string> = {
    'To Do': t('statusToDo'),
    'In Progress': t('statusInProgress'),
    Review: t('statusReview'),
    Done: t('statusDone'),
  };
  const filtered = tasks.filter(
    (task) =>
      (!customCondition || matchedIds.has(task.id)) &&
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
      `${task.key ?? ''} ${task.title} ${task.description ?? ''}`
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
  appendCustomCondition(exportParams, customCondition);
  const exportQuery = exportParams.size ? `?${exportParams}` : '';

  return {
    hasProjects,
    projectId,
    language,
    openRequest,
    onRequestHandled,
    userEmail,
    readOnly,
    confirm,
    t,
    draft,
    panelOpen,
    setPanelOpen,
    savedTitle,
    setSavedTitle,
    loading,
    tasks,
    dependencyBlocked,
    wipLimits: wipResult.data?.limits ?? [],
    setTasks,
    stories,
    sprints,
    members,
    projectRole,
    blocking,
    setBlocking,
    discussion,
    setDiscussion,
    role,
    canWrite,
    editing,
    setEditing,
    foreignDraft,
    draftLinks,
    draftLinksError,
    draftLinksLoading,
    editorStories,
    editorSprints,
    editorMembers,
    title,
    setTitle,
    description,
    setDescription,
    points,
    setPoints,
    dueDate,
    setDueDate,
    storyId,
    setStoryId,
    sprintId,
    setSprintId,
    assigneeEmail,
    setAssigneeEmail,
    query,
    setQuery,
    statusFilter,
    setStatusFilter,
    sprintFilter,
    setSprintFilter,
    assigneeFilter,
    setAssigneeFilter,
    dueFilter,
    setDueFilter,
    blockedOnly,
    setBlockedOnly,
    labelFilter,
    setLabelFilter,
    draggedTaskId,
    setDraggedTaskId,
    message,
    setMessage,
    saving,
    setSaving,
    pendingTasks,
    busyTasks,
    setBusyTasks,
    beginTaskChange,
    endTaskChange,
    clearForm,
    editTask,
    saveTask,
    updateTask,
    toggleBlocked,
    deleteTask,
    columns,
    labels,
    filtered,
    exportParams,
    exportQuery,
    customCondition,
    setCustomCondition,
    customMatches,
  };
}
export type UseTaskBoardState = ReturnType<typeof useTaskBoard>;
