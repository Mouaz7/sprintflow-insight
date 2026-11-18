import { useState, type FormEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import type { Epic, Story, TaskEstimate } from '../../api/contracts';
import { storiesQuery, tasksQuery } from '../../api/queries';
import { useT } from '../../shared/i18n/LanguageProvider';
import { Drawer, Notice } from '../../shared/ui/ui';
import { ErrorState, Skeleton } from '../../shared/ui/States';
import { FormGuide } from '../../shared/ui/Guidance';
import { roadmapText } from './i18n';
import {
  estimatesQuery,
  problemText,
  useRoadmapActions,
  type RoadmapAction,
  type EpicDraft,
} from './queries';
import { WorkChoice } from './WorkChoice';

export type Editor =
  | { kind: 'epic'; epic?: Epic | undefined }
  | { kind: 'story' | 'dependency' | 'estimate' };

export function RoadmapEditor({
  projectId,
  epics,
  editor,
  onClose,
  selectedEpic,
}: {
  projectId: number;
  epics: Epic[];
  editor: Editor;
  onClose: () => void;
  selectedEpic: number | undefined;
}) {
  const t = useT(roadmapText);
  const mutation = useRoadmapActions(projectId);
  const [dirty, setDirty] = useState(false);
  const title =
    editor.kind === 'epic'
      ? t(editor.epic ? 'editEpic' : 'newEpic')
      : t(
          editor.kind === 'story'
            ? 'assignStory'
            : editor.kind === 'dependency'
              ? 'addDependency'
              : 'editEstimate',
        );
  async function save(action: RoadmapAction) {
    try {
      await mutation.mutateAsync(action);
      onClose();
    } catch {
      /* Keep the draft and show the translated problem. */
    }
  }
  return (
    <Drawer
      title={title}
      onClose={onClose}
      busy={mutation.isPending}
      dirty={dirty}
    >
      <div onChange={() => setDirty(true)}>
        <FormGuide
          topic={editor.kind === 'story' ? 'storyEpic' : editor.kind}
        />
        <Notice error={problemText(mutation.error, t)} />
        {editor.kind === 'epic' ? (
          <EpicForm epic={editor.epic} busy={mutation.isPending} save={save} />
        ) : (
          <WorkForm
            projectId={projectId}
            epics={epics}
            kind={editor.kind}
            busy={mutation.isPending}
            save={save}
            selectedEpic={selectedEpic}
          />
        )}
      </div>
    </Drawer>
  );
}

function EpicForm({
  epic,
  busy,
  save,
}: {
  epic: Epic | undefined;
  busy: boolean;
  save: (action: RoadmapAction) => Promise<void>;
}) {
  const t = useT(roadmapText);
  const [draft, setDraft] = useState<EpicDraft>(() => ({
    title: epic?.title ?? '',
    description: epic?.description ?? '',
    color: epic?.color ?? '#4f46e5',
    startDate: epic?.startDate ?? null,
    targetDate: epic?.targetDate ?? null,
  }));
  const [error, setError] = useState('');
  function submit(event: FormEvent) {
    event.preventDefault();
    if (
      draft.startDate &&
      draft.targetDate &&
      draft.targetDate < draft.startDate
    ) {
      setError(t('datesOrdered'));
      return;
    }
    setError('');
    void save({ kind: 'epic', draft, epic });
  }
  return (
    <form className="editor-form" onSubmit={submit}>
      <label>
        {t('epicTitle')}
        <input
          required
          maxLength={120}
          value={draft.title}
          placeholder={t('titleExample')}
          onChange={(event) =>
            setDraft({ ...draft, title: event.target.value })
          }
        />
      </label>
      <label>
        {t('epicDescription')}
        <textarea
          maxLength={2000}
          value={draft.description ?? ''}
          placeholder={t('descriptionExample')}
          onChange={(event) =>
            setDraft({ ...draft, description: event.target.value })
          }
        />
      </label>
      <label>
        {t('color')}
        <input
          type="color"
          value={draft.color}
          onChange={(event) =>
            setDraft({ ...draft, color: event.target.value })
          }
        />
      </label>
      <div className="roadmap-form-pair">
        <label>
          {t('startDate')}
          <input
            type="date"
            min="1900-01-01"
            max="9999-12-31"
            value={draft.startDate ?? ''}
            onChange={(event) =>
              setDraft({ ...draft, startDate: event.target.value || null })
            }
          />
        </label>
        <label>
          {t('targetDate')}
          <input
            type="date"
            min={draft.startDate ?? '1900-01-01'}
            max="9999-12-31"
            value={draft.targetDate ?? ''}
            onChange={(event) =>
              setDraft({ ...draft, targetDate: event.target.value || null })
            }
          />
        </label>
      </div>
      <Notice error={error} />
      <button className="primary-action" disabled={busy}>
        {t(busy ? 'saving' : 'save')}
      </button>
    </form>
  );
}

function WorkForm({
  projectId,
  epics,
  kind,
  busy,
  save,
  selectedEpic,
}: {
  projectId: number;
  epics: Epic[];
  kind: 'story' | 'dependency' | 'estimate';
  busy: boolean;
  save: (action: RoadmapAction) => Promise<void>;
  selectedEpic: number | undefined;
}) {
  const t = useT(roadmapText);
  const tasks = useQuery({
    ...tasksQuery(projectId),
    enabled: kind !== 'story',
  });
  const stories = useQuery({
    ...storiesQuery(projectId),
    enabled: kind === 'story',
  });
  const estimates = useQuery({
    ...estimatesQuery(projectId),
    enabled: kind === 'estimate',
  });
  const [story, setStory] = useState<Story>();
  const [taskId, setTaskId] = useState<number>();
  const [blockerId, setBlockerId] = useState<number>();
  const [epicId, setEpicId] = useState<number | undefined>(selectedEpic);
  // Capture the estimate and revision when selecting work, retaining them during background refreshes.
  const [estimate, setEstimate] = useState<TaskEstimate>();
  const [values, setValues] = useState({
    optimistic: '',
    mostLikely: '',
    pessimistic: '',
  });
  const [error, setError] = useState('');
  const result = kind === 'story' ? stories : tasks;
  if (result.isError || (kind === 'estimate' && estimates.isError))
    return (
      <ErrorState
        detail={t('loadFailed')}
        onRetry={() => {
          void result.refetch();
          if (kind === 'estimate') void estimates.refetch();
        }}
      />
    );
  if (result.isPending || (kind === 'estimate' && estimates.isPending))
    return <Skeleton label={t('loading')} />;
  if (!result.data?.length)
    return <p>{t(kind === 'story' ? 'noStories' : 'noTasks')}</p>;
  function submit(event: FormEvent) {
    event.preventDefault();
    setError('');
    if (kind === 'story' && story)
      void save({ kind, story, epicId: epicId ?? null });
    if (kind === 'dependency' && taskId && blockerId)
      void save({ kind, blockerTaskId: blockerId, blockedTaskId: taskId });
    if (kind === 'estimate' && taskId) {
      const draft = {
        optimistic: Number(values.optimistic),
        mostLikely: Number(values.mostLikely),
        pessimistic: Number(values.pessimistic),
      };
      if (
        draft.optimistic > draft.mostLikely ||
        draft.mostLikely > draft.pessimistic
      ) {
        setError(t('ordered'));
        return;
      }
      void save({ kind, taskId, draft, version: estimate?.version });
    }
  }
  return (
    <form className="editor-form" onSubmit={submit}>
      {kind === 'story' ? (
        <>
          <WorkChoice
            label={t('chooseStory')}
            items={stories.data ?? []}
            value={story?.id}
            onChange={(id) => {
              const current = stories.data?.find((item) => item.id === id);
              setStory(current);
              setEpicId(selectedEpic ?? current?.epicId ?? undefined);
            }}
          />
          <label>
            {t('epic')}
            <select
              value={epicId ?? ''}
              onChange={(event) =>
                setEpicId(
                  event.target.value ? Number(event.target.value) : undefined,
                )
              }
            >
              <option value="">{t('noEpic')}</option>
              {epics.map((epic) => (
                <option key={epic.id} value={epic.id}>
                  {epic.title}
                </option>
              ))}
            </select>
          </label>
        </>
      ) : (
        <>
          {kind === 'dependency' && (
            <WorkChoice
              label={t('blocker')}
              items={tasks.data ?? []}
              value={blockerId}
              onChange={setBlockerId}
            />
          )}
          <WorkChoice
            label={t(kind === 'dependency' ? 'blocked' : 'chooseTask')}
            items={(tasks.data ?? []).filter(
              (task) => kind !== 'dependency' || task.id !== blockerId,
            )}
            value={taskId}
            onChange={(id) => {
              setTaskId(id);
              const current = estimates.data?.find(
                (item) => item.taskId === id,
              );
              setEstimate(current);
              setValues({
                optimistic: current ? String(current.optimistic) : '',
                mostLikely: current ? String(current.mostLikely) : '',
                pessimistic: current ? String(current.pessimistic) : '',
              });
            }}
          />
        </>
      )}
      {kind === 'estimate' && (
        <>
          <p>{t('estimateHint')}</p>
          {(['optimistic', 'mostLikely', 'pessimistic'] as const).map((key) => (
            <label key={key}>
              {t(key === 'mostLikely' ? 'likely' : key)}
              <input
                required
                type="number"
                min="0"
                max="1000"
                step="0.01"
                value={values[key]}
                onChange={(event) =>
                  setValues({ ...values, [key]: event.target.value })
                }
              />
            </label>
          ))}
        </>
      )}
      <Notice error={error} />
      <button
        className="primary-action"
        disabled={
          busy ||
          (kind === 'story'
            ? !story
            : !taskId ||
              (kind === 'dependency' && (!blockerId || blockerId === taskId)))
        }
      >
        {t(busy ? 'saving' : 'save')}
      </button>
    </form>
  );
}
