import { useQuery } from '@tanstack/react-query';
import { FormEvent, useState } from 'react';
import { api } from '../../api/client';
import type { Story } from '../../api/contracts';
import { sprintsQuery } from '../../api/queries';
import { useDraftProject } from '../../shared/lib/useDraftProject';
import { useLanguage, useT } from '../../shared/i18n/LanguageProvider';
import { useConfirm } from '../../shared/ui/ui';
import { planningText } from './i18n';
export type BacklogManagerProps = {
  projectId: number;
  items: Story[];
  onChanged: (saved?: Story) => void;
};
export function useBacklog({
  projectId,
  items,
  onChanged,
}: BacklogManagerProps) {
  const confirm = useConfirm();
  const { language } = useLanguage();
  const t = useT(planningText);
  const [panelOpen, setPanelOpen] = useState(false);
  const draft = useDraftProject(projectId, language);
  const [editing, setEditing] = useState<Story>();
  const sprintProjectId = panelOpen && !editing ? draft.id : projectId;
  const sprintList = useQuery({
    ...sprintsQuery(sprintProjectId ?? 0),
    enabled: sprintProjectId !== undefined,
  });
  const sprints = sprintList.data ?? [];
  const sprintLoading = sprintList.isPending;
  const sprintError = sprintList.error?.message ?? '';
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
    if (saving || sprintLoading || sprintError || (!editing && draft.blocked))
      return;
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
      const saved = await api<Story>(
        editing ? `/stories/${editing.id}` : '/stories',
        {
          method: editing ? 'PUT' : 'POST',
          ...(editing
            ? { headers: { 'If-Match': `"${editing.version}"` } }
            : {}),
          body: JSON.stringify(body),
        },
      );
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
    if (!(await confirm(t('confirmDeleteStory', { title: story.title }))))
      return;
    try {
      await api<unknown>(`/workspace/trash/story/${story.id}`, {
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

  return {
    projectId,
    items,
    language,
    onChanged,
    confirm,
    t,
    panelOpen,
    setPanelOpen,
    draft,
    editing,
    setEditing,
    sprints,
    sprintLoading,
    sprintError,
    sprintProjectId,
    title,
    setTitle,
    description,
    setDescription,
    criteria,
    setCriteria,
    points,
    setPoints,
    priority,
    setPriority,
    businessValue,
    setBusinessValue,
    sprintId,
    setSprintId,
    error,
    setError,
    saving,
    setSaving,
    savedTitle,
    setSavedTitle,
    begin,
    save,
    remove,
    search,
    setSearch,
    sprintFilter,
    setSprintFilter,
    sortBy,
    setSortBy,
    visibleStories,
    move,
  };
}
export type UseBacklogState = ReturnType<typeof useBacklog>;
