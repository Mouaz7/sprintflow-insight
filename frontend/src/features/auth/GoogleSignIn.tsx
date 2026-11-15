import { useQuery } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { api, clearCsrfToken } from '../../api/client';
import type { User } from '../../api/contracts';
import { useLanguage, useT } from '../../shared/i18n/LanguageProvider';
import { Notice } from '../../shared/ui/ui';
import { googleScriptUrl } from './googleScriptUrl';
import { authText } from './i18n';
import { googleConfigQuery } from './queries';
type GoogleIdentity = {
  accounts: {
    id: {
      initialize: (options: {
        client_id: string;
        callback: (response: { credential: string }) => void;
      }) => void;
      renderButton: (
        parent: HTMLElement,
        options: {
          theme: string;
          size: string;
          text: string;
          width: number;
          locale: string;
        },
      ) => void;
    };
  };
};
declare global {
  interface Window {
    google?: GoogleIdentity;
  }
}
let scriptPromise: Promise<void> | undefined;
function loadGoogle() {
  if (window.google) return Promise.resolve();
  if (!scriptPromise)
    scriptPromise = new Promise<void>((resolve, reject) => {
      const script = document.createElement('script');
      script.src = googleScriptUrl();
      const nonce = document.querySelector<HTMLMetaElement>(
        'meta[property="csp-nonce"]',
      )?.nonce;
      if (nonce) script.nonce = nonce;
      script.async = true;
      const timeout = setTimeout(() => {
        script.remove();
        scriptPromise = undefined;
        reject(new Error('Google sign-in could not be loaded.'));
      }, 10000);
      script.onload = () => {
        clearTimeout(timeout);
        resolve();
      };
      script.onerror = () => {
        clearTimeout(timeout);
        script.remove();
        scriptPromise = undefined;
        reject(new Error('Google sign-in could not be loaded.'));
      };
      document.head.append(script);
    });
  return scriptPromise;
}
export function GoogleSignIn({
  darkMode,
  onSignedIn,
}: {
  darkMode: boolean;
  onSignedIn: (user: User) => Promise<void>;
}) {
  const { language } = useLanguage();
  const t = useT(authText);
  const locale = t('googleLocale');
  const root = useRef<HTMLDivElement>(null);
  const latestSignedIn = useRef(onSignedIn);
  useEffect(() => {
    latestSignedIn.current = onSignedIn;
  });
  const busyRef = useRef(false);
  const [busy, setBusy] = useState(false);
  const [revision, setRevision] = useState(0);
  const config = useQuery(googleConfigQuery);
  const clientId = config.data?.clientId ?? '';
  // One attempt per configuration, language, theme and retry. Its outcome and
  // errors only count for that attempt, so a new attempt starts clean.
  const attempt = `${clientId}|${language}|${darkMode}|${revision}`;
  const [outcome, setOutcome] = useState<{
    attempt: string;
    ready: boolean;
    failure?: string;
  }>();
  const [shownError, setShownError] = useState<{
    attempt: string;
    text: string;
  }>();
  const error = shownError?.attempt === attempt ? shownError.text : '';
  const setError = (text: string) => setShownError({ attempt, text });
  const settled = outcome?.attempt === attempt;
  const ready = settled && outcome.ready;
  const loading = config.isPending || (!!clientId && !settled);
  const notConfigured = 'Google sign-in is not configured yet.';
  const failure = config.isError
    ? config.error.message
    : !clientId
      ? notConfigured
      : ((settled ? outcome.failure : undefined) ?? notConfigured);
  useEffect(() => {
    if (!clientId) return;
    const run = new AbortController();
    async function initialize() {
      try {
        await loadGoogle();
        if (run.signal.aborted || !root.current || !window.google) return;
        window.google.accounts.id.initialize({
          client_id: clientId,
          callback: async ({ credential }) => {
            if (busyRef.current || run.signal.aborted) return;
            busyRef.current = true;
            setBusy(true);
            setShownError(undefined);
            try {
              const user = await api<User>('/auth/google', {
                method: 'POST',
                body: JSON.stringify({ credential }),
              });
              clearCsrfToken();
              await latestSignedIn.current(user);
            } catch (cause) {
              if (!run.signal.aborted)
                setShownError({ attempt, text: (cause as Error).message });
            } finally {
              busyRef.current = false;
              setBusy(false);
            }
          },
        });
        root.current.replaceChildren();
        window.google.accounts.id.renderButton(root.current, {
          theme: darkMode ? 'filled_black' : 'outline',
          size: 'large',
          text: 'continue_with',
          width: Math.min(
            400,
            Math.max(
              200,
              Math.floor(root.current.getBoundingClientRect().width),
            ),
          ),
          locale,
        });
        setOutcome({ attempt, ready: true });
      } catch (cause) {
        if (!run.signal.aborted)
          setOutcome({
            attempt,
            ready: false,
            failure: (cause as Error).message,
          });
      }
    }
    void initialize();
    return () => run.abort();
  }, [attempt, clientId, darkMode, locale]);
  return (
    <div className="google-sign-in">
      <div
        ref={root}
        className={'google-button-slot' + (ready ? ' ready' : '')}
        aria-busy={busy}
        style={{ pointerEvents: busy ? 'none' : undefined }}
      />
      {!ready && (
        <button
          className="google-action"
          type="button"
          disabled={loading || busy}
          onClick={() => {
            setError(failure);
            if (failure !== notConfigured) {
              setRevision((value) => value + 1);
              if (config.isError) void config.refetch();
            }
          }}
        >
          <span aria-hidden="true">G</span>
          {t(loading ? 'loadingGoogle' : 'continueWithGoogle')}
        </button>
      )}
      {busy && <p role="status">{t('signingInWithGoogle')}</p>}
      <Notice error={error} />
    </div>
  );
}
