import { localizeMessage } from '../../shared/i18n/messages';
import { FormGuide, useFieldExample } from '../../shared/ui/Guidance';
import { Drawer } from '../../shared/ui/ui';
import type { UseBacklogState } from './useBacklog';
export function StoryEditor({ controller }: { controller: UseBacklogState }) {
  const {
    editing,
    t,
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
  const fieldExample = useFieldExample();
  return (
    <Drawer
      title={t(editing ? 'editStory' : 'newStory')}
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
        <FormGuide topic="story" />
        <fieldset disabled={saving}>
          <label>
            {t('title')}
            <input
              required
              maxLength={255}
              placeholder={fieldExample('storyTitle')}
              value={title}
              onChange={(event) => setTitle(event.target.value)}
            />
          </label>
          <label>
            {t('description')}
            <textarea
              aria-label={t('description')}
              rows={3}
              placeholder={fieldExample('storyDescription')}
              value={description}
              onChange={(event) => setDescription(event.target.value)}
            />
          </label>
          <label>
            {t('acceptanceCriteria')}
            <textarea
              aria-label={t('acceptanceCriteria')}
              required
              rows={3}
              placeholder={fieldExample('criteria')}
              value={criteria}
              onChange={(event) => setCriteria(event.target.value)}
            />
          </label>
          <div className="form-row">
            <label>
              {t('storyPoints')}
              <select
                aria-label={t('storyPoints')}
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
              {t('priority')}
              <select
                aria-label={t('priority')}
                value={priority}
                onChange={(event) => setPriority(Number(event.target.value))}
              >
                <option value={1}>{t('high')}</option>
                <option value={2}>{t('medium')}</option>
                <option value={3}>{t('low')}</option>
              </select>
            </label>
            <label>
              {t('businessValue')}
              <select
                aria-label={t('businessValue')}
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
              {t('sprint')}
              <select
                aria-label={t('sprint')}
                value={sprintId}
                onChange={(event) => setSprintId(event.target.value)}
              >
                <option value="">{t('productBacklog')}</option>
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
            {localizeMessage(error, language)}
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
            {t(saving ? 'saving' : editing ? 'saveChanges' : 'createStory')}
          </button>
        </div>
      </form>
    </Drawer>
  );
}
