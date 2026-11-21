import { lazy, Suspense } from 'react';
import { localizeMessage } from '../../shared/i18n/messages';
import {
  DATE_ERROR,
  MAX_DATE,
  MIN_DATE,
  normalizePoints,
  validDate,
} from '../../shared/lib/formValues';
import { DateInput } from '../../shared/ui/DateInput';
import { FormGuide, useFieldExample } from '../../shared/ui/Guidance';
import { Drawer } from '../../shared/ui/ui';
import type { UseTaskBoardState } from './useTaskBoard';

const TaskDuplicateSuggestions = lazy(
  () => import('./TaskDuplicateSuggestions'),
);
export function TaskEditor({ controller }: { controller: UseTaskBoardState }) {
  const {
    editing,
    t,
    language,
    draft,
    saving,
    setStoryId,
    setSprintId,
    setAssigneeEmail,
    setMessage,
    clearForm,
    projectId,
    title,
    description,
    points,
    dueDate,
    storyId,
    sprintId,
    assigneeEmail,
    saveTask,
    setTitle,
    setDescription,
    setPoints,
    setDueDate,
    draftLinksLoading,
    editorStories,
    editorSprints,
    editorMembers,
    foreignDraft,
    draftLinksError,
    message,
  } = controller;
  const fieldExample = useFieldExample();
  return (
    <Drawer
      title={
        editing
          ? `${t('editTask')}${editing.key ? ` · ${editing.key}` : ''}`
          : t('newTask')
      }
      projectSelection={
        !editing && draft.projects
          ? {
              projects: draft.projects,
              value: draft.id,
              disabled: saving,
              onChange: (id) => {
                draft.change(id);
                setStoryId('');
                setSprintId('');
                setAssigneeEmail('');
                setMessage('');
              },
            }
          : undefined
      }
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
        sprintId !== (editing?.sprintId ? String(editing.sprintId) : '') ||
        assigneeEmail !== (editing?.assigneeEmail ?? '')
      }
    >
      <form className="editor-form" onSubmit={saveTask}>
        <FormGuide topic="task" />
        <fieldset disabled={saving}>
          <div className="task-form-grid">
            <div>
              <label>
                {t('fieldTitle')}
                <input
                  required
                  maxLength={255}
                  value={title}
                  onChange={(event) => setTitle(event.target.value)}
                  placeholder={fieldExample('taskTitle')}
                />
              </label>
              {!editing && draft.id && title.trim().length >= 4 && (
                <Suspense>
                  <TaskDuplicateSuggestions
                    projectId={draft.id}
                    title={title}
                    language={language}
                    linkToBoard={draft.id === projectId}
                  />
                </Suspense>
              )}
            </div>
            <label>
              {t('fieldDescription')}
              <input
                placeholder={fieldExample('taskDescription')}
                value={description}
                onChange={(event) => setDescription(event.target.value)}
              />
            </label>
            <label>
              {t('storyPoints')}
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
              {t('dueDate')}
              <DateInput
                min={MIN_DATE}
                max={MAX_DATE}
                value={dueDate}
                onBlur={() => {
                  if (dueDate && !validDate(dueDate))
                    setMessage(localizeMessage(DATE_ERROR, language));
                }}
                onChange={(value) => {
                  setDueDate(value);
                  setMessage('');
                }}
              />
            </label>
            <label style={{ alignContent: 'start' }}>
              {t('userStory')}
              <select
                aria-label={t('userStory')}
                disabled={draftLinksLoading}
                value={storyId}
                onChange={(event) => {
                  const value = event.target.value;
                  setStoryId(value);
                  const story = editorStories.find(
                    (item) => String(item.id) === value,
                  );
                  setSprintId(story?.sprintId ? String(story.sprintId) : '');
                }}
              >
                <option value="">{t('none')}</option>
                {editorStories.map((story) => (
                  <option value={story.id} key={story.id}>
                    {story.title}
                  </option>
                ))}
              </select>
            </label>
            <label>
              {t('sprint')}
              <select
                aria-label={t('sprint')}
                disabled={draftLinksLoading || !!storyId}
                aria-describedby={
                  storyId ? 'task-sprint-inheritance' : undefined
                }
                value={sprintId}
                onChange={(event) => setSprintId(event.target.value)}
              >
                <option value="">{t('productBacklog')}</option>
                {editorSprints.map((sprint) => (
                  <option value={sprint.id} key={sprint.id}>
                    {sprint.name}
                  </option>
                ))}
              </select>
              {storyId && (
                <small id="task-sprint-inheritance">
                  {t('followsStorySprint')}
                </small>
              )}
            </label>
            <label>
              {t('assignee')}
              <select
                aria-label={t('assignee')}
                disabled={draftLinksLoading}
                value={assigneeEmail}
                onChange={(event) => setAssigneeEmail(event.target.value)}
              >
                <option value="">{t('unassigned')}</option>
                {editorMembers.map((member) => (
                  <option key={member.id} value={member.email}>
                    {member.email}
                  </option>
                ))}
              </select>
            </label>
          </div>
        </fieldset>
        {!editing && draft.error && (
          <p className="form-message error" role="alert">
            {localizeMessage(draft.error, language)}
          </p>
        )}
        {foreignDraft && draftLinksError && (
          <p className="form-message error" role="alert">
            {localizeMessage(draftLinksError, language)}
          </p>
        )}
        {message && (
          <p className="form-message error" role="alert">
            {localizeMessage(message, language)}
          </p>
        )}
        <div className="form-actions">
          <button
            className="primary-action"
            disabled={
              saving || (!editing && (draft.blocked || draftLinksLoading))
            }
          >
            {saving
              ? t('saving')
              : editing
                ? t('saveChanges')
                : t('createTask')}
          </button>
        </div>
      </form>
    </Drawer>
  );
}
