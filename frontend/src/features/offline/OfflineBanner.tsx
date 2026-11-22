import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { useT } from '../../shared/i18n/LanguageProvider';
import { useIsOnline } from '../../shared/offline/connectivity';
import { offlineText } from '../../shared/offline/i18n';
import {
  discardOfflineChange,
  listenToOfflineChanges,
  replayOfflineChanges,
  retryOfflineChange,
} from '../../shared/offline/operations';
import {
  offlineState,
  type QueuedOperation,
} from '../../shared/offline/storage';
import './offline.css';

export default function OfflineBanner({ accountId }: { accountId: number }) {
  const t = useT(offlineText);
  const client = useQueryClient();
  const online = useIsOnline();
  const [operations, setOperations] = useState<QueuedOperation[]>([]);
  const [busy, setBusy] = useState(false);
  const [failed, setFailed] = useState(false);
  const banner = useRef<HTMLElement>(null);
  const focusedAction = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    let active = true;
    const refresh = () => {
      void offlineState()
        .then((state) => {
          if (active)
            setOperations(state?.user.id === accountId ? state.operations : []);
        })
        .catch(() => undefined);
    };
    const stop = listenToOfflineChanges(refresh);
    refresh();
    return () => {
      active = false;
      stop();
    };
  }, [accountId]);
  useEffect(() => {
    if (online)
      void replayOfflineChanges(client, accountId).catch(() => undefined);
  }, [accountId, client, online]);
  useLayoutEffect(() => {
    const previous = focusedAction.current;
    if (!previous || previous.isConnected) return;
    focusedAction.current = null;
    if (document.activeElement !== document.body) return;
    const next = banner.current?.querySelector<HTMLButtonElement>('li button');
    (next ?? banner.current?.closest('main'))?.focus();
  }, [operations]);

  async function run(button: HTMLButtonElement, action: () => Promise<void>) {
    if (!online || busy) return;
    if (document.activeElement === button) focusedAction.current = button;
    setBusy(true);
    setFailed(false);
    try {
      await action();
    } catch {
      setFailed(true);
    } finally {
      setBusy(false);
    }
  }
  async function discard(operation: QueuedOperation) {
    await discardOfflineChange(accountId, operation);
    void client.invalidateQueries({
      queryKey: ['project', operation.projectId],
    });
    await replayOfflineChanges(client, accountId);
  }
  const visible = !online || operations.length > 0;
  const review = operations.filter((operation) => operation.state);
  const status = [
    !online && t('offline'),
    operations.length > 0 && t('pending', { count: operations.length }),
    online && review.length > 0 && t('needsReview', { count: review.length }),
    busy && t('busy'),
  ]
    .filter(Boolean)
    .join(' ');
  return (
    <section
      ref={banner}
      className={visible ? 'offline-banner' : undefined}
      aria-label={visible ? t(online ? 'retry' : 'offline') : undefined}
    >
      <div className={visible ? 'offline-summary' : 'offline-summary sr-only'}>
        <p role="status" aria-atomic="true">
          {status}
        </p>
        {online && operations.some((operation) => !operation.state) && (
          <button
            type="button"
            aria-disabled={busy}
            onClick={(event) =>
              void run(event.currentTarget, () =>
                replayOfflineChanges(client, accountId),
              )
            }
          >
            {t('retry')}
          </button>
        )}
      </div>
      {visible && failed && <p role="alert">{t('failed')}</p>}
      {review.length > 0 && (
        <ul>
          {review.map((operation) => (
            <li key={operation.operationId}>
              <div>
                <strong>
                  {t(operation.kind)} · {operation.title}
                </strong>
                <p>
                  {t(operation.state === 'conflict' ? 'conflict' : 'blocked')}
                </p>
              </div>
              <span className="offline-actions">
                <button
                  type="button"
                  aria-disabled={!online || busy}
                  onClick={(event) =>
                    void run(event.currentTarget, () => discard(operation))
                  }
                >
                  {t(operation.state === 'conflict' ? 'keep' : 'discard')}
                </button>
                {operation.state === 'conflict' && (
                  <button
                    type="button"
                    aria-disabled={!online || busy}
                    onClick={(event) =>
                      void run(event.currentTarget, () =>
                        retryOfflineChange(client, accountId, operation),
                      )
                    }
                  >
                    {t('redo')}
                  </button>
                )}
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
