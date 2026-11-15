import { localizeMessage } from '../../shared/i18n/messages';
import { FormGuide, fieldExample } from '../../shared/ui/Guidance';
import { Drawer } from '../../shared/ui/ui';
import type { UseBacklogState } from './useBacklog';
export function StoryEditor({ controller }: { controller: UseBacklogState }) {
  const {
    editing,
    sv,
    language,
    draft,
    saving,
    setError,
    setSprintId,
    setPanelOpen,
    projectId,
    title,
    description,
    criteria,
    points,
    priority,
    businessValue,
    sprintId,
    save,
    setTitle,
    setDescription,
    setCriteria,
    setPoints,
    setPriority,
    setBusinessValue,
    sprints,
    sprintError,
    error,
    sprintLoading,
  } = controller;
  return (
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
      projectSelection={
        !editing && draft.projects
          ? {
              projects: draft.projects,
              value: draft.id,
              disabled: saving,
              onChange: (id) => {
                draft.change(id);
                setError('');
                setSprintId('');
              },
            }
          : undefined
      }
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
                onChange={(event) => setPriority(Number(event.target.value))}
              >
                <option value={1}>{sv ? 'Hög' : 'High'}</option>
                <option value={2}>{sv ? 'Medel' : 'Medium'}</option>
                <option value={3}>{sv ? 'Låg' : 'Low'}</option>
              </select>
            </label>
            <label>
              {sv ? 'Affärsvärde (1–5)' : 'Business value (1–5)'}
              <select
                aria-label={sv ? 'Affärsvärde (1–5)' : 'Business value (1–5)'}
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
        {!editing && draft.error && (
          <p className="form-message error" role="alert">
            {localizeMessage(draft.error, language)}
          </p>
        )}
        {sprintError && (
          <p className="form-message error" role="alert">
            {localizeMessage(sprintError, language)}
          </p>
        )}
        {error && (
          <p className="form-message error" role="alert">
            {localizeMessage(error, sv ? 'SV' : 'EN')}
          </p>
        )}
        <div className="form-actions">
          <button
            className="primary-action"
            disabled={
              saving ||
              sprintLoading ||
              !!sprintError ||
              (!editing && draft.blocked)
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
                  ? 'Skapa berättelse'
                  : 'Create story'}
          </button>
        </div>
      </form>
    </Drawer>
  );
}
