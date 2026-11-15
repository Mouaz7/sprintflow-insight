import { useState, type FormEvent } from 'react';
import { api } from '../api/client';
import type { Activity, Language } from '../api/contracts';
import { FormGuide, fieldExample } from './Guidance';
import { Drawer, Notice, useConfirm } from './ui';
import { InterfaceIcon } from './InterfaceIcons';
import { activitySummary } from './activityText';

export function ActivityTimeline({
  projectId,
  items,
  language,
  canManage,
  onChanged,
}: {
  projectId: number;
  items: Activity[];
  language: Language;
  canManage: boolean;
  onChanged: () => void;
}) {
  const sv = language === 'SV';
  const confirm = useConfirm();
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [editing, setEditing] = useState<Activity>();
  async function remove(item?: Activity) {
    if (
      busy ||
      !(await confirm(
        item
          ? sv
            ? 'Ta bort den här uppdateringen?'
            : 'Delete this update?'
          : sv
            ? 'Radera all historik i projektet? Det går inte att ångra.'
            : 'Delete all project history? This cannot be undone.',
      ))
    )
      return;
    setBusy(true);
    setError('');
    try {
      await api(`/projects/${projectId}/activity${item ? '/' + item.id : ''}`, {
        method: 'DELETE',
      });
      onChanged();
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : sv
            ? 'Historiken kunde inte raderas.'
            : 'Could not delete history.',
      );
    } finally {
      setBusy(false);
    }
  }
  const groups = new Map<string, Activity[]>();
  items
    .slice()
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    .forEach((item) => {
      const date = new Date(item.createdAt).toLocaleDateString(
        sv ? 'sv-SE' : 'en-GB',
        { day: 'numeric', month: 'long', year: 'numeric' },
      );
      groups.set(date, [...(groups.get(date) ?? []), item]);
    });
  return (
    <>
      <div className="collection-actions">
        <span>
          {items.length}{' '}
          {sv
            ? items.length === 1
              ? 'händelse'
              : 'händelser'
            : items.length === 1
              ? 'event'
              : 'events'}
        </span>
        {canManage && items.length > 0 && (
          <button
            className="danger-action secondary-action"
            disabled={busy}
            onClick={() => remove()}
          >
            {busy
              ? sv
                ? 'Raderar…'
                : 'Clearing…'
              : sv
                ? 'Radera all historik'
                : 'Clear all history'}
          </button>
        )}
      </div>
      <Notice language={language} error={error} />
      {items.length ? (
        <div className="activity-timeline">
          {[...groups].map(([date, events]) => (
            <section className="timeline-day" key={date}>
              <h2>{date}</h2>
              <div className="timeline-events">
                {events.map((item) => (
                  <article key={item.id}>
                    <span className="timeline-dot">
                      <InterfaceIcon name="updates" />
                    </span>
                    <div className="timeline-event-content">
                      <time dateTime={item.createdAt}>
                        {new Date(item.createdAt).toLocaleTimeString(
                          sv ? 'sv-SE' : 'en-GB',
                          { hour: '2-digit', minute: '2-digit' },
                        )}
                      </time>
                      <h3>{activitySummary(item, language)}</h3>
                      <small>{item.actorEmail}</small>
                    </div>
                    {canManage && (
                      <div className="card-actions">
                        <button
                          type="button"
                          disabled={busy}
                          onClick={() => setEditing(item)}
                        >
                          {sv ? 'Redigera' : 'Edit'}
                        </button>
                        <button
                          className="danger-action"
                          type="button"
                          disabled={busy}
                          onClick={() => remove(item)}
                        >
                          {sv ? 'Ta bort' : 'Delete'}
                        </button>
                      </div>
                    )}
                  </article>
                ))}
              </div>
            </section>
          ))}
        </div>
      ) : (
        <div className="empty-state">
          <InterfaceIcon name="updates" />
          <h2>{sv ? 'Här börjar historien' : 'The story starts here'}</h2>
          <p>
            {sv
              ? 'Projektets aktivitet visas här när teamet börjar arbeta.'
              : 'Project activity appears here when the team starts working.'}
          </p>
        </div>
      )}
      {editing && (
        <ActivityEditor
          activity={editing}
          projectId={projectId}
          language={language}
          onClose={() => setEditing(undefined)}
          onSaved={onChanged}
        />
      )}
    </>
  );
}

function ActivityEditor({
  activity,
  projectId,
  language,
  onClose,
  onSaved,
}: {
  activity: Activity;
  projectId: number;
  language: Language;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [summary, setSummary] = useState(activity.summary);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const sv = language === 'SV';
  async function save(e: FormEvent) {
    e.preventDefault();
    if (busy) return;
    if (!summary.trim()) {
      setError(sv ? 'Ange en uppdateringstext.' : 'Enter an update summary.');
      return;
    }
    setBusy(true);
    setError('');
    try {
      await api(`/projects/${projectId}/activity/${activity.id}`, {
        method: 'PUT',
        body: JSON.stringify({ summary: summary.trim() }),
      });
      onSaved();
      onClose();
    } catch (cause) {
      setError(
        cause instanceof Error ? cause.message : 'Could not save this update.',
      );
    } finally {
      setBusy(false);
    }
  }
  return (
    <Drawer
      title={sv ? 'Redigera uppdatering' : 'Edit update'}
      language={language}
      busy={busy}
      dirty={summary !== activity.summary}
      onClose={onClose}
    >
      <form className="editor-form" onSubmit={save}>
        <FormGuide topic="activity" language={language} />
        <label>
          {sv ? 'Uppdateringstext' : 'Update summary'}
          <textarea
            aria-label={sv ? 'Uppdateringstext' : 'Update summary'}
            required
            maxLength={500}
            rows={5}
            disabled={busy}
            placeholder={fieldExample('update', language)}
            value={summary}
            onChange={(e) => setSummary(e.target.value)}
          />
        </label>
        <Notice language={language} error={error} />
        <div className="editor-footer">
          <button className="primary-action" disabled={busy}>
            {busy ? (sv ? 'Sparar…' : 'Saving…') : sv ? 'Spara' : 'Save'}
          </button>
        </div>
      </form>
    </Drawer>
  );
}
