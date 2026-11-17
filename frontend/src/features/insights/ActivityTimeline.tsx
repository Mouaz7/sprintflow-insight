import { useState, type FormEvent } from 'react';
import { api } from '../../api/client';
import type { Activity } from '../../api/contracts';
import { useLanguage, useT } from '../../shared/i18n/LanguageProvider';
import { FormGuide, useFieldExample } from '../../shared/ui/Guidance';
import { InterfaceIcon } from '../../shared/ui/InterfaceIcons';
import { Drawer, Notice, useConfirm } from '../../shared/ui/ui';
import { activitySummary } from './activityText';
import { insightsText } from './i18n';

export function ActivityTimeline({
  projectId,
  items,
  canManage,
  onChanged,
}: {
  projectId: number;
  items: Activity[];
  canManage: boolean;
  onChanged: () => void;
}) {
  const t = useT(insightsText);
  const { language, format } = useLanguage();
  const confirm = useConfirm();
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [editing, setEditing] = useState<Activity>();
  async function remove(item?: Activity) {
    if (
      busy ||
      !(await confirm(t(item ? 'confirmDeleteUpdate' : 'confirmClearHistory')))
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
      setError(cause instanceof Error ? cause.message : t('clearFailed'));
    } finally {
      setBusy(false);
    }
  }
  const groups = new Map<string, Activity[]>();
  items
    .slice()
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    .forEach((item) => {
      const date = format.date(item.createdAt, {
        day: 'numeric',
        month: 'long',
        year: 'numeric',
      });
      groups.set(date, [...(groups.get(date) ?? []), item]);
    });
  return (
    <>
      <div className="collection-actions">
        <span>{t('eventCount', { count: items.length })}</span>
        {canManage && items.length > 0 && (
          <button
            className="danger-action secondary-action"
            disabled={busy}
            onClick={() => remove()}
          >
            {t(busy ? 'clearing' : 'clearHistory')}
          </button>
        )}
      </div>
      <Notice error={error} />
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
                          format.locale,
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
                          {t('edit')}
                        </button>
                        <button
                          className="danger-action"
                          type="button"
                          disabled={busy}
                          onClick={() => remove(item)}
                        >
                          {t('delete')}
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
          <h2>{t('storyStarts')}</h2>
          <p>{t('activityAppears')}</p>
        </div>
      )}
      {editing && (
        <ActivityEditor
          activity={editing}
          projectId={projectId}
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
  onClose,
  onSaved,
}: {
  activity: Activity;
  projectId: number;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [summary, setSummary] = useState(activity.summary);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const t = useT(insightsText);
  const example = useFieldExample();
  async function save(e: FormEvent) {
    e.preventDefault();
    if (busy) return;
    if (!summary.trim()) {
      setError(t('enterSummary'));
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
      title={t('editUpdate')}
      busy={busy}
      dirty={summary !== activity.summary}
      onClose={onClose}
    >
      <form className="editor-form" onSubmit={save}>
        <FormGuide topic="activity" />
        <label>
          {t('updateSummary')}
          <textarea
            aria-label={t('updateSummary')}
            required
            maxLength={500}
            rows={5}
            disabled={busy}
            placeholder={example('update')}
            value={summary}
            onChange={(e) => setSummary(e.target.value)}
          />
        </label>
        <Notice error={error} />
        <div className="editor-footer">
          <button className="primary-action" disabled={busy}>
            {t(busy ? 'saving' : 'save')}
          </button>
        </div>
      </form>
    </Drawer>
  );
}
