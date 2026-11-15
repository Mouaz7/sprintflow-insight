import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { api } from '../api/client';
import type { Language, Notification } from '../api/contracts';
import { localizeMessage } from '../shared/i18n/messages';
import { Notice } from '../shared/ui/ui';

const notificationsKey = ['notifications'];

export function NotificationBell({
  language,
  onProject,
}: {
  language: Language;
  onProject: (id: number) => void;
}) {
  const sv = language === 'SV';
  const client = useQueryClient();
  const notifications = useQuery({
    queryKey: notificationsKey,
    queryFn: () => api<Notification[]>('/notifications'),
    select: (data) => (Array.isArray(data) ? data : []),
    // Polls only while the page is visible, as before.
    refetchInterval: 60000,
  });
  const items = notifications.data ?? [];
  const [open, setOpen] = useState(false);
  const [markError, setMarkError] = useState<{ message: string; at: number }>();
  const [busy, setBusy] = useState(false);
  const root = useRef<HTMLDivElement>(null);
  const button = useRef<HTMLButtonElement>(null);
  // A later successful load clears an earlier marking error.
  const error =
    markError && markError.at > notifications.dataUpdatedAt
      ? markError.message
      : notifications.isError
        ? notifications.error.message
        : '';
  useEffect(() => {
    const reload = () => {
      void client.refetchQueries({ queryKey: notificationsKey });
    };
    window.addEventListener('sprintflow:notifications', reload);
    window.addEventListener('focus', reload);
    return () => {
      window.removeEventListener('sprintflow:notifications', reload);
      window.removeEventListener('focus', reload);
    };
  }, [client]);
  useEffect(() => {
    if (!open) return;
    function outside(event: MouseEvent) {
      if (!root.current?.contains(event.target as Node)) setOpen(false);
    }
    function escape(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setOpen(false);
        button.current?.focus();
      }
    }
    document.addEventListener('mousedown', outside);
    document.addEventListener('keydown', escape);
    return () => {
      document.removeEventListener('mousedown', outside);
      document.removeEventListener('keydown', escape);
    };
  }, [open]);
  async function mark(item?: Notification) {
    if (busy) return;
    setBusy(true);
    try {
      await api(
        item ? `/notifications/${item.id}/read` : '/notifications/read',
        { method: 'PUT' },
      );
      client.setQueryData<Notification[]>(notificationsKey, (current) =>
        current?.map((value) =>
          !item || item.id === value.id ? { ...value, read: true } : value,
        ),
      );
    } catch (cause) {
      setMarkError({ message: (cause as Error).message, at: Date.now() });
    } finally {
      setBusy(false);
    }
  }
  const unread = items.filter((item) => !item.read).length;
  const unreadBadge = unread > 99 ? '99+' : String(unread);
  const unreadTotal =
    unread > 99 ? ` (${unread} ${sv ? 'totalt' : 'total'})` : '';
  const label = unread
    ? `${sv ? 'Notiser' : 'Notifications'}, ${unreadBadge} ${sv ? 'olästa' : 'unread'}${unreadTotal}`
    : sv
      ? 'Notiser'
      : 'Notifications';
  return (
    <div className="notification-control" ref={root}>
      <button
        ref={button}
        type="button"
        className="theme-toggle notification-bell"
        aria-label={label}
        aria-expanded={open}
        aria-controls="notification-list"
        onClick={() => setOpen((value) => !value)}
      >
        <svg
          width="18"
          height="18"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.7"
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden="true"
        >
          <path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4" />
        </svg>
        {unread > 0 && (
          <span className="notification-count" aria-hidden="true">
            {unreadBadge}
          </span>
        )}
      </button>
      {open && (
        <section
          id="notification-list"
          className="notification-popover"
          aria-label={sv ? 'Notislista' : 'Notification list'}
        >
          <header>
            <h2>{sv ? 'Notiser' : 'Notifications'}</h2>
            <button
              onClick={() => setOpen(false)}
              aria-label={sv ? 'Stäng notiser' : 'Close notifications'}
            >
              ×
            </button>
          </header>
          <Notice language={language} error={error} />
          {unread > 0 && (
            <button disabled={busy} onClick={() => mark()}>
              {sv ? 'Markera alla som lästa' : 'Mark all read'}
            </button>
          )}
          {items.length === 0 && (
            <p>
              {sv
                ? 'Du har inga notiser ännu.'
                : 'You have no notifications yet.'}
            </p>
          )}
          <ul>
            {items.map((item) => (
              <li key={item.id} className={item.read ? 'read' : 'unread'}>
                <button
                  onClick={() => {
                    onProject(item.projectId);
                    setOpen(false);
                    void mark(item);
                  }}
                >
                  {localizeMessage(item.message, language)}
                  <small>
                    {new Date(item.createdAt).toLocaleString(
                      sv ? 'sv-SE' : 'en-GB',
                    )}
                  </small>
                </button>
                {!item.read && (
                  <button disabled={busy} onClick={() => mark(item)}>
                    {sv ? 'Markera läst' : 'Mark read'}
                  </button>
                )}
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}
