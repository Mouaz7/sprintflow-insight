import type { Language } from '../../api/contracts';
import type { RealtimeStatus, Viewer } from './connection';
import { translator } from '../../shared/i18n/lexicon';
import { realtimeText } from './i18n';

type Props = {
  viewers: Viewer[];
  selfId: number;
  status: RealtimeStatus;
  language: Language;
};

const SHOWN = 4;

function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (!parts.length) return '?';
  return parts
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .join('');
}

/** Who else has the current project open, and whether updates are live. */
export function PresenceAvatars({ viewers, selfId, status, language }: Props) {
  const t = translator(realtimeText, language);
  const others = viewers.filter((viewer) => viewer.userId !== selfId);
  const fallback = t('teamMember');
  const names = others.map((viewer) => {
    const name = viewer.name || fallback;
    const tasks = viewer.taskIds ?? [];
    return tasks.length
      ? t('openTasks', { name, tasks: tasks.map((id) => `#${id}`).join(', ') })
      : name;
  });
  const label =
    status !== 'live'
      ? t('reconnecting')
      : others.length
        ? t('viewers', { names: names.join(', ') })
        : t('live');
  return (
    <div
      className={`presence presence-${status}`}
      role="group"
      aria-label={label}
      title={label}
    >
      <span className="presence-dot" aria-hidden="true" />
      {others.slice(0, SHOWN).map((viewer, index) => (
        <span
          className="avatar presence-avatar"
          key={viewer.userId}
          aria-hidden="true"
        >
          {initials(names[index] ?? fallback)}
        </span>
      ))}
      {others.length > SHOWN && (
        <span
          className="avatar presence-avatar presence-more"
          aria-hidden="true"
        >
          +{others.length - SHOWN}
        </span>
      )}
    </div>
  );
}
