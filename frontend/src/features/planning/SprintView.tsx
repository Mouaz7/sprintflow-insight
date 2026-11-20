import { FormEvent, lazy, Suspense, useState } from 'react';
import { api } from '../../api/client';
import type { Sprint } from '../../api/contracts';
import { localizeMessage } from '../../shared/i18n/messages';
import {
  DATE_ERROR,
  MAX_DATE,
  MIN_DATE,
  normalizePoints,
  validDate,
  validPoints,
} from '../../shared/lib/formValues';
import { useDraftProject } from '../../shared/lib/useDraftProject';
import { DateInput } from '../../shared/ui/DateInput';
import { FormGuide, useFieldExample } from '../../shared/ui/Guidance';
import { useLanguage, useT } from '../../shared/i18n/LanguageProvider';
import { Drawer, useConfirm } from '../../shared/ui/ui';
import { planningText, sprintStatusKey } from './i18n';

const SprintPlanDrawer = lazy(() =>
  import('./SprintPlanDrawer').then(({ SprintPlanDrawer }) => ({
    default: SprintPlanDrawer,
  })),
);

export function SprintManager({
  projectId,
  items,
  onChanged,
}: {
  projectId: number;
  items: Sprint[];
  onChanged: (saved?: Sprint) => void;
}) {
  const confirm = useConfirm();
  const { language } = useLanguage();
  const t = useT(planningText);
  const fieldExample = useFieldExample();
  const [panelOpen, setPanelOpen] = useState(false);
  const draft = useDraftProject(projectId, language);
  const [editing, setEditing] = useState<Sprint>();
  const [planning, setPlanning] = useState<Sprint>();
  const [name, setName] = useState('');
  const [goal, setGoal] = useState('');
  const [status, setStatus] = useState('Planned');
  const [points, setPoints] = useState('0');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [savedTitle, setSavedTitle] = useState('');
  function begin(sprint?: Sprint) {
    draft.reset();
    setSavedTitle('');
    setPanelOpen(true);
    setEditing(sprint);
    setName(sprint?.name ?? '');
    setGoal(sprint?.goal ?? '');
    setStatus(sprint?.status ?? 'Planned');
    setPoints(String(sprint?.totalStoryPoints ?? 0));
    setStartDate(sprint?.startDate ?? '');
    setEndDate(sprint?.endDate ?? '');
    setError('');
  }
  const dateError = localizeMessage(DATE_ERROR, language);
  function changeDate(value: string, update: (value: string) => void) {
    update(value);
    setError('');
  }
  async function save(event: FormEvent) {
    event.preventDefault();
    if (saving || (!editing && draft.blocked)) return;
    if (!validPoints(points, 2147483647)) {
      setError(t('pointsRange'));
      return;
    }
    if (!validDate(startDate) || !validDate(endDate)) {
      setError(dateError);
      return;
    }
    if (startDate && endDate && endDate < startDate) {
      setError(t('endBeforeStart'));
      return;
    }
    setSaving(true);
    setError('');
    const body = {
      ...(editing ?? {}),
      name,
      goal,
      status,
      totalStoryPoints: Number(points),
      startDate: startDate || null,
      endDate: endDate || null,
      projectId: editing ? projectId : draft.id,
    };
    try {
      const saved = await api<Sprint>(
        editing ? `/sprints/${editing.id}` : '/sprints',
        {
          method: editing ? 'PUT' : 'POST',
          ...(editing
            ? { headers: { 'If-Match': `"${editing.version}"` } }
            : {}),
          body: JSON.stringify(body),
        },
      );
      if (!draft.reveal(saved.projectId)) onChanged(saved);
      setSavedTitle(saved.name);
      setPanelOpen(false);
    } catch (cause) {
      setError(
        cause instanceof Error ? cause.message : 'Could not save the sprint.',
      );
    } finally {
      setSaving(false);
    }
  }
  async function remove(sprint: Sprint) {
    if (!(await confirm(t('confirmDeleteSprint', { title: sprint.name }))))
      return;
    try {
      await api<unknown>(`/workspace/trash/sprint/${sprint.id}`, {
        method: 'PUT',
        headers: { 'If-Match': `"${sprint.version}"` },
        body: JSON.stringify({ restore: false }),
      });
      onChanged();
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : 'Could not delete this sprint.',
      );
    }
  }
  return (
    <div className="manager-layout">
      <div className="collection-actions">
        <span>{t('sprintCount', { count: items.length })}</span>
        <button className="primary-action" onClick={() => begin()}>
          + {t('newSprint')}
        </button>
      </div>
      {!panelOpen && savedTitle && (
        <p className="form-message success" role="status">
          {t('sprintSaved', { title: savedTitle })}
        </p>
      )}
      {!panelOpen && error && (
        <p className="form-message error" role="alert">
          {localizeMessage(error, language)}
        </p>
      )}
      {panelOpen && (
        <Drawer
          title={t(editing ? 'editSprint' : 'newSprint')}
          projectSelection={
            !editing && draft.projects
              ? {
                  projects: draft.projects,
                  value: draft.id,
                  disabled: saving,
                  onChange: (id) => {
                    draft.change(id);
                    setError('');
                  },
                }
              : undefined
          }
          onClose={() => setPanelOpen(false)}
          busy={saving}
          dirty={
            (!editing && draft.id !== projectId) ||
            name !== (editing?.name ?? '') ||
            goal !== (editing?.goal ?? '') ||
            status !== (editing?.status ?? 'Planned') ||
            points !== String(editing?.totalStoryPoints ?? 0) ||
            startDate !== (editing?.startDate ?? '') ||
            endDate !== (editing?.endDate ?? '')
          }
        >
          <form className="editor-form" onSubmit={save}>
            <FormGuide topic="sprint" />
            <fieldset disabled={saving}>
              <label>
                {t('name')}
                <input
                  required
                  maxLength={255}
                  placeholder={fieldExample('sprintName')}
                  value={name}
                  onChange={(event) => setName(event.target.value)}
                />
              </label>
              <label>
                {t('sprintGoal')}
                <textarea
                  aria-label={t('sprintGoal')}
                  rows={3}
                  placeholder={fieldExample('sprintGoal')}
                  value={goal}
                  onChange={(event) => setGoal(event.target.value)}
                />
              </label>
              <div className="form-row">
                <label>
                  {t('status')}
                  <select
                    aria-label={t('status')}
                    value={status}
                    onChange={(event) => setStatus(event.target.value)}
                  >
                    {['Planned', 'Active', 'Completed', 'Cancelled'].map(
                      (item) => (
                        <option key={item} value={item}>
                          {t(
                            sprintStatusKey[
                              item as keyof typeof sprintStatusKey
                            ],
                          )}
                        </option>
                      ),
                    )}
                  </select>
                </label>
                <label>
                  {t('plannedPoints')}
                  <input
                    type="number"
                    min={0}
                    max={2147483647}
                    step={1}
                    required
                    value={points}
                    onChange={(event) =>
                      setPoints(normalizePoints(event.target.value))
                    }
                  />
                </label>
                <label>
                  {t('startDate')}
                  <DateInput
                    required
                    min={MIN_DATE}
                    max={MAX_DATE}
                    value={startDate}
                    onBlur={() => {
                      if (startDate && !validDate(startDate))
                        setError(dateError);
                    }}
                    onChange={(value) => changeDate(value, setStartDate)}
                  />
                </label>
                <label>
                  {t('endDate')}
                  <DateInput
                    required
                    min={validDate(startDate) ? startDate : MIN_DATE}
                    max={MAX_DATE}
                    value={endDate}
                    onBlur={() => {
                      if (endDate && !validDate(endDate)) setError(dateError);
                    }}
                    onChange={(value) => changeDate(value, setEndDate)}
                  />
                </label>
              </div>
            </fieldset>
            {!editing && draft.error && (
              <p className="form-message error" role="alert">
                {localizeMessage(draft.error, language)}
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
                disabled={saving || (!editing && draft.blocked)}
              >
                {t(
                  saving ? 'saving' : editing ? 'saveChanges' : 'createSprint',
                )}
              </button>
            </div>
          </form>
        </Drawer>
      )}
      <div className="sprint-collection">
        {items.length ? (
          items
            .slice()
            .sort((a, b) =>
              a.status === 'Active'
                ? -1
                : b.status === 'Active'
                  ? 1
                  : (b.startDate ?? '').localeCompare(a.startDate ?? ''),
            )
            .map((sprint) => (
              <article
                className={
                  'sprint-card' +
                  (sprint.status === 'Active' ? ' active-sprint' : '')
                }
                key={sprint.id}
              >
                <header>
                  <span className="eyebrow">
                    {t(
                      sprint.status === 'Active'
                        ? 'teamFocus'
                        : 'sprintEyebrow',
                    )}
                  </span>
                  <span className="status-badge">
                    {sprint.status in sprintStatusKey
                      ? t(
                          sprintStatusKey[
                            sprint.status as keyof typeof sprintStatusKey
                          ],
                        )
                      : sprint.status}
                  </span>
                </header>
                <h2>{sprint.name}</h2>
                <p>{sprint.goal || t('noSprintGoal')}</p>
                <div className="sprint-details">
                  <span>
                    <small>{t('period')}</small>
                    <strong>
                      {sprint.startDate ?? '—'} → {sprint.endDate ?? '—'}
                    </strong>
                  </span>
                  <span>
                    <small>{t('planned')}</small>
                    <strong>
                      {t('pointCount', { count: sprint.totalStoryPoints })}
                    </strong>
                  </span>
                </div>
                <div className="card-actions">
                  {sprint.status !== 'Completed' && (
                    <button type="button" onClick={() => setPlanning(sprint)}>
                      {t('suggestSprint')}
                    </button>
                  )}
                  <button type="button" onClick={() => begin(sprint)}>
                    {t('edit')}
                  </button>
                  <button
                    type="button"
                    className="danger-action"
                    onClick={() => remove(sprint)}
                  >
                    {t('delete')}
                  </button>
                </div>
              </article>
            ))
        ) : (
          <div className="empty-state">{t('noSprints')}</div>
        )}
      </div>
      {planning && (
        <Suspense fallback={<p role="status">{t('loadingSprintPlan')}</p>}>
          <SprintPlanDrawer
            key={`${projectId}:${planning.id}`}
            projectId={projectId}
            sprint={planning}
            onClose={() => setPlanning(undefined)}
          />
        </Suspense>
      )}
    </div>
  );
}
