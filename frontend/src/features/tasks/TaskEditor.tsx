import { localizeMessage } from '../../shared/i18n/messages';
import {
  DATE_ERROR,
  MAX_DATE,
  MIN_DATE,
  normalizePoints,
  validDate,
} from '../../shared/lib/formValues';
import { DateInput } from '../../shared/ui/DateInput';
import { FormGuide, fieldExample } from '../../shared/ui/Guidance';
import { Drawer } from '../../shared/ui/ui';
import type { UseTaskBoardState } from './useTaskBoard';
export function TaskEditor({ controller }: { controller: UseTaskBoardState }) {
  const {
    editing,
    sv,
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
  return (
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
                    setMessage(localizeMessage(DATE_ERROR, sv ? 'SV' : 'EN'));
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
                  setSprintId(story?.sprintId ? String(story.sprintId) : '');
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
                onChange={(event) => setAssigneeEmail(event.target.value)}
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
            {localizeMessage(message, sv ? 'SV' : 'EN')}
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
  );
}
