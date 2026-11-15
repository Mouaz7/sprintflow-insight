import { FormEvent, useEffect, useRef, useState } from 'react';
import { api } from '../../api/client';
import type { ProjectMember, Sprint, Story, Task } from '../../api/contracts';
import { localizeMessage } from '../../shared/i18n/messages';
import {
  DATE_ERROR,
  validDate,
  validPoints,
} from '../../shared/lib/formValues';
import { useDraftProject } from '../../shared/lib/useDraftProject';
import { useConfirm } from '../../shared/ui/ui';
export type TasksViewProps = {
  userEmail?: string;
  readOnly?: boolean;
  hasProjects: boolean;
  projectId?: number | undefined;
  language: 'EN' | 'SV';
  openRequest?: number;
  /** Changes when the project changed elsewhere (realtime); reloads quietly. */
  refreshSignal?: number;
  onRequestHandled?: () => void;
};
export function useTaskBoard({
  hasProjects,
  projectId,
  language,
  openRequest = 0,
  refreshSignal = 0,
  onRequestHandled,
  userEmail = '',
  readOnly = false,
}: TasksViewProps) {
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
  const [draftLinks, setDraftLinks] = useState<{
    id: number | undefined;
    stories: Story[];
    sprints: Sprint[];
    members: ProjectMember[];
  }>();
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
    ])
      .then(([stories, sprints, members]) => {
        if (live) setDraftLinks({ id: draft.id, stories, sprints, members });
      })
      .catch((cause: Error) => {
        if (live) setDraftLinksError(cause.message);
      });
    return () => {
      live = false;
    };
  }, [foreignDraft, draft.id]);
  const draftLinksLoading = foreignDraft && draftLinks?.id !== draft.id;
  const editorStories = foreignDraft
    ? draftLinks && draftLinks.id === draft.id
      ? draftLinks.stories
      : []
    : stories;
  const editorSprints = foreignDraft
    ? draftLinks && draftLinks.id === draft.id
      ? draftLinks.sprints
      : []
    : sprints;
  const editorMembers = foreignDraft
    ? draftLinks && draftLinks.id === draft.id
      ? draftLinks.members
      : []
    : members;
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
  useEffect(() => {
    if (!refreshSignal || !projectId) return;
    let live = true;
    Promise.all([
      api<Task[]>(`/tasks?projectId=${projectId}`),
      api<Story[]>(`/stories/project/${projectId}`),
      api<Sprint[]>(`/sprints?projectId=${projectId}`),
    ])
      .then(([taskItems, storyItems, sprintItems]) => {
        if (!live) return;
        setTasks(taskItems);
        setStories(storyItems);
        setSprints(sprintItems);
      })
      // A failed background refresh keeps the current board; the next change retries.
      .catch(() => {});
    return () => {
      live = false;
    };
  }, [refreshSignal, projectId]);
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
          ...(editing
            ? { headers: { 'If-Match': `"${editing.version}"` } }
            : {}),
          body: JSON.stringify(body),
        },
      );
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
    sv,
    draft,
    panelOpen,
    setPanelOpen,
    savedTitle,
    setSavedTitle,
    loading,
    setLoading,
    tasks,
    setTasks,
    stories,
    setStories,
    sprints,
    setSprints,
    members,
    setMembers,
    projectRole,
    setProjectRole,
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
    setDraftLinks,
    draftLinksError,
    setDraftLinksError,
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
  };
}
export type UseTaskBoardState = ReturnType<typeof useTaskBoard>;
