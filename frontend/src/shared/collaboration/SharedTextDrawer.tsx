import { useEffect, useEffectEvent, useRef, useState } from 'react';
import { useLanguage } from '../i18n/LanguageProvider';
import { Drawer, Notice } from '../ui/ui';
import { CrdtSession, emptyView, type SessionView } from './CrdtSession';
import type { CollaborationScope } from './contracts';
import './collaboration.css';

const labels = {
  EN: {
    title: 'Shared text',
    description: 'Shared description',
    note: 'Shared note',
    start: 'Start co-editing',
    loading: 'Loading shared text…',
    readOnly: 'Read only',
    connecting: 'Connecting. Edits stay in this tab until syncing resumes.',
    pending: 'Syncing edits…',
    synced: 'Synced',
    people: 'Writing here',
    length: 'Shorten the shared text to save it.',
    blank: 'Enter a note to save the shared text.',
  },
  SV: {
    title: 'Samredigering',
    description: 'Delad beskrivning',
    note: 'Delad anteckning',
    start: 'Starta samredigering',
    loading: 'Läser in delad text…',
    readOnly: 'Skrivskyddad',
    connecting:
      'Ansluter. Ändringar ligger kvar i fliken tills synkningen återupptas.',
    pending: 'Synkar ändringar…',
    synced: 'Synkad',
    people: 'Skriver här',
    length: 'Korta den delade texten för att spara den.',
    blank: 'Fyll i en anteckning för att spara den delade texten.',
  },
} as const;

export default function SharedTextDrawer({
  scope: { projectId, target, id },
  userEmail,
  onClose,
  onProjected,
}: {
  scope: CollaborationScope;
  userEmail: string;
  onClose: () => void;
  onProjected: () => void;
}) {
  const { language } = useLanguage();
  const t = labels[language];
  const [view, setView] = useState(emptyView);
  const session = useRef<CrdtSession | null>(null);
  const notifyProjected = useEffectEvent(onProjected);

  useEffect(() => {
    const current = new CrdtSession(
      { projectId, target, id },
      userEmail,
      setView,
      () => notifyProjected(),
    );
    session.current = current;
    return () => {
      current.close();
      session.current = null;
    };
  }, [projectId, target, id, userEmail]);

  useEffect(() => {
    if (!view.unsaved) return;
    const protect = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = true;
    };
    window.addEventListener('beforeunload', protect);
    return () => window.removeEventListener('beforeunload', protect);
  }, [view.unsaved]);

  const status = !view.ready
    ? t.loading
    : !view.editable
      ? t.readOnly
      : !view.active
        ? null
        : !view.connected
          ? t.connecting
          : view.unsaved
            ? t.pending
            : t.synced;

  return (
    <Drawer title={t.title} onClose={onClose} dirty={view.unsaved}>
      <div className="editor-form">
        <Notice error={view.error} />
        {status && <p role="status">{status}</p>}
        {view.ready && (
          <>
            <label>
              {target === 'TASK' ? t.description : t.note}
              <textarea
                rows={8}
                value={view.text}
                readOnly={!view.editable || !view.active}
                onChange={(event) =>
                  session.current?.change(event.target.value)
                }
                onSelect={(event) =>
                  session.current?.select(
                    event.currentTarget.selectionStart,
                    event.currentTarget.selectionEnd,
                  )
                }
                onBlur={() => session.current?.clearSelection()}
              />
            </label>
            {view.active && (
              <p>
                {view.text.length} / {view.maxLength}
              </p>
            )}
            {view.invalid && (
              <p role="alert">
                {view.text.length > view.maxLength ? t.length : t.blank}
              </p>
            )}
            {!view.active && view.editable && (
              <button
                className="primary-action"
                onClick={() => void session.current?.activate()}
              >
                {t.start}
              </button>
            )}
            {!!view.presence.length && (
              <RemoteCarets view={view} label={t.people} />
            )}
          </>
        )}
      </div>
    </Drawer>
  );
}

function RemoteCarets({ view, label }: { view: SessionView; label: string }) {
  const sorted = [...view.presence].sort((a, b) => a.head - b.head);
  return (
    <div className="shared-text-presence" aria-label={label} role="group">
      <p>{label}</p>
      <div className="shared-text-cursors">
        {sorted.map((person, index) => {
          const before = view.text.slice(
            sorted[index - 1]?.head ?? 0,
            person.head,
          );
          return (
            <span key={person.id}>
              {before}
              <mark title={person.name} aria-label={person.name}>
                ▏
              </mark>
            </span>
          );
        })}
        {view.text.slice(sorted.at(-1)?.head ?? 0)}
      </div>
      <p>
        {Array.from(new Set(sorted.map((person) => person.name))).join(', ')}
      </p>
    </div>
  );
}
