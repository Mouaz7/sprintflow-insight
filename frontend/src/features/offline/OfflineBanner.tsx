import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
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

  async function run(action: () => Promise<void>) {
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
  if (online && operations.length === 0) return null;
  return (
    <section
      className="offline-banner"
      aria-label={t(online ? 'retry' : 'offline')}
    >
      <div className="offline-summary">
        <p role="status">
          {!online && t('offline')}
          {!online && operations.length > 0 && ' '}
          {operations.length > 0 && t('pending', { count: operations.length })}
          {busy && ` ${t('busy')}`}
        </p>
        {online && operations.some((operation) => !operation.state) && (
          <button
            type="button"
            disabled={busy}
            onClick={() =>
              void run(() => replayOfflineChanges(client, accountId))
            }
          >
            {t('retry')}
          </button>
        )}
      </div>
      {failed && <p role="alert">{t('failed')}</p>}
      {operations.some((operation) => operation.state) && (
        <ul>
          {operations
            .filter((operation) => operation.state)
            .map((operation) => (
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
                    disabled={!online || busy}
                    onClick={() => void run(() => discard(operation))}
                  >
                    {t(operation.state === 'conflict' ? 'keep' : 'discard')}
                  </button>
                  {operation.state === 'conflict' && (
                    <button
                      type="button"
                      disabled={!online || busy}
                      onClick={() =>
                        void run(() =>
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
