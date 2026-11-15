import { FormGuide, fieldExample } from '../../shared/ui/Guidance';
import { DateInput } from '../../shared/ui/DateInput';
import {
  MIN_DATE,
  MAX_DATE,
  DATE_ERROR,
  validDate,
  normalizePoints,
  validPoints,
} from '../../shared/lib/formValues';
import { useDraftProject } from '../../shared/lib/useDraftProject';
import { localizeMessage } from '../../shared/i18n/messages';
import { FormEvent, useState } from 'react';
import { api } from '../../api/client';
import type { Sprint } from '../../api/contracts';
import { Drawer, useConfirm } from '../../shared/ui/ui';

export function SprintManager({
  projectId,
  items,
  language,
  onChanged,
}: {
  projectId: number;
  items: Sprint[];
  language: 'EN' | 'SV';
  onChanged: (saved?: Sprint) => void;
}) {
  const confirm = useConfirm();
  const sv = language === 'SV';
  const [panelOpen, setPanelOpen] = useState(false);
  const draft = useDraftProject(projectId, language);
  const [editing, setEditing] = useState<Sprint>();
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
  const dateError = localizeMessage(DATE_ERROR, sv ? 'SV' : 'EN');
  function changeDate(value: string, update: (value: string) => void) {
    update(value);
    setError('');
  }
  async function save(event: FormEvent) {
    event.preventDefault();
    if (saving || (!editing && draft.blocked)) return;
    if (!validPoints(points, 2147483647)) {
      setError(
        sv
          ? 'Ange ett heltal mellan 0 och 2147483647.'
          : 'Enter a whole number between 0 and 2147483647.',
      );
      return;
    }
    if (!validDate(startDate) || !validDate(endDate)) {
      setError(dateError);
      return;
    }
    if (startDate && endDate && endDate < startDate) {
      setError(
        sv
          ? 'Slutdatum måste vara samma dag eller efter startdatum.'
          : 'End date must be on or after the start date.',
      );
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
      const saved = await api<Sprint>(editing ? `/sprints/${editing.id}` : '/sprints', {
        method: editing ? 'PUT' : 'POST',
        headers: editing ? { 'If-Match': `"${editing.version}"` } : undefined,
        body: JSON.stringify(body),
      });
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
    if (
      !(await confirm(
        sv
          ? `Ta bort sprinten “${sprint.name}”?`
          : `Delete sprint “${sprint.name}”?`,
      ))
    )
      return;
    try {
      await api<void>(`/workspace/trash/sprint/${sprint.id}`, {
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
        <span>
          {items.length}{' '}
          {sv
            ? items.length === 1
              ? 'sprint'
              : 'sprintar'
            : items.length === 1
              ? 'sprint'
              : 'sprints'}
        </span>
        <button className="primary-action" onClick={() => begin()}>
          + {sv ? 'Ny sprint' : 'New sprint'}
        </button>
      </div>
      {!panelOpen && savedTitle && (
        <p className="form-message success" role="status">
          {sv ? `Sprinten ”${savedTitle}” har sparats.` : `Sprint “${savedTitle}” saved.`}
        </p>
      )}
      {!panelOpen && error && (
        <p className="form-message error" role="alert">
          {localizeMessage(error, sv ? 'SV' : 'EN')}
        </p>
      )}
      {panelOpen && (
        <Drawer
          title={
            editing
              ? sv
                ? 'Redigera sprint'
                : 'Edit sprint'
              : sv
                ? 'Ny sprint'
                : 'New sprint'
          }
          language={language}
          projectSelection={!editing && draft.projects ? {
            projects: draft.projects, value: draft.id, disabled: saving,
            onChange: (id) => {
              draft.change(id);
              setError('');
            },
          } : undefined}
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
            <FormGuide topic="sprint" language={language} />
            <fieldset disabled={saving}>
              <label>
                {sv ? 'Namn' : 'Name'}
                <input
                  required
                  maxLength={255}
                  placeholder={fieldExample('sprintName', language)}
                  value={name}
                  onChange={(event) => setName(event.target.value)}
                />
              </label>
              <label>
                {sv ? 'Sprintmål' : 'Sprint goal'}
                <textarea
                  aria-label={sv ? 'Sprintmål' : 'Sprint goal'}
                  rows={3}
                  placeholder={fieldExample('sprintGoal', language)}
                  value={goal}
                  onChange={(event) => setGoal(event.target.value)}
                />
              </label>
              <div className="form-row">
                <label>
                  {sv ? 'Status' : 'Status'}
                  <select
                    aria-label={sv ? 'Status' : 'Status'}
                    value={status}
                    onChange={(event) => setStatus(event.target.value)}
                  >
                    {['Planned', 'Active', 'Completed', 'Cancelled'].map(
                      (item) => (
                        <option key={item} value={item}>
                          {sv
                            ? {
                                Planned: 'Planerad',
                                Active: 'Aktiv',
                                Completed: 'Slutförd',
                                Cancelled: 'Avbruten',
                              }[item]
                            : item}
                        </option>
                      ),
                    )}
                  </select>
                </label>
                <label>
                  {sv ? 'Planerade poäng' : 'Planned points'}
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
                  {sv ? 'Startdatum' : 'Start date'}
                  <DateInput
                    language={language}
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
                  {sv ? 'Slutdatum' : 'End date'}
                  <DateInput
                    language={language}
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
            {!editing && draft.error && <p className="form-message error" role="alert">{localizeMessage(draft.error, language)}</p>}
            {error && (
              <p className="form-message error" role="alert">
                {localizeMessage(error, sv ? 'SV' : 'EN')}
              </p>
            )}
            <div className="form-actions">
              <button className="primary-action" disabled={saving || (!editing && draft.blocked)}>
                {saving
                  ? sv
                    ? 'Sparar…'
                    : 'Saving…'
                  : editing
                    ? sv
                      ? 'Spara ändringar'
                      : 'Save changes'
                    : sv
                      ? 'Skapa sprint'
                      : 'Create sprint'}
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
                    {sprint.status === 'Active'
                      ? sv
                        ? 'TEAMETS FOKUS'
                        : 'TEAM FOCUS'
                      : sv
                        ? 'SPRINT'
                        : 'SPRINT'}
                  </span>
                  <span className="status-badge">
                    {sv
                      ? ({
                          Planned: 'Planerad',
                          Active: 'Aktiv',
                          Completed: 'Slutförd',
                          Cancelled: 'Avbruten',
                        }[sprint.status] ?? sprint.status)
                      : sprint.status}
                  </span>
                </header>
                <h2>{sprint.name}</h2>
                <p>
                  {sprint.goal ||
                    (sv ? 'Inget sprintmål ännu.' : 'No sprint goal yet.')}
                </p>
                <div className="sprint-details">
                  <span>
                    <small>{sv ? 'Period' : 'Period'}</small>
                    <strong>
                      {sprint.startDate ?? '—'} → {sprint.endDate ?? '—'}
                    </strong>
                  </span>
                  <span>
                    <small>{sv ? 'Planerat' : 'Planned'}</small>
                    <strong>
                      {sprint.totalStoryPoints} {sv ? 'poäng' : 'points'}
                    </strong>
                  </span>
                </div>
                <div className="card-actions">
                  <button type="button" onClick={() => begin(sprint)}>
                    {sv ? 'Redigera' : 'Edit'}
                  </button>
                  <button
                    type="button"
                    className="danger-action"
                    onClick={() => remove(sprint)}
                  >
                    {sv ? 'Ta bort' : 'Delete'}
                  </button>
                </div>
              </article>
            ))
        ) : (
          <div className="empty-state">
            {sv
              ? 'Inga sprintar ännu. Skapa en sprint för att planera arbetet.'
              : 'No sprints yet. Create a sprint to plan work.'}
          </div>
        )}
      </div>
    </div>
  );
}
