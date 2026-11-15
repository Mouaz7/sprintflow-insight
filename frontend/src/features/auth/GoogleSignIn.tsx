import { useEffect, useRef, useState } from 'react';
import { api, clearCsrfToken } from '../../api/client';
import type { Language, User } from '../../api/contracts';
import { Notice } from '../../shared/ui/ui';
import { googleScriptUrl } from './googleScriptUrl';
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
  language,
  darkMode,
  onSignedIn,
}: {
  language: Language;
  darkMode: boolean;
  onSignedIn: (user: User) => Promise<void>;
}) {
  const sv = language === 'SV';
  const root = useRef<HTMLDivElement>(null);
  const latestSignedIn = useRef(onSignedIn);
  useEffect(() => {
    latestSignedIn.current = onSignedIn;
  });
  const busyRef = useRef(false);
  const [busy, setBusy] = useState(false);
  const [ready, setReady] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [revision, setRevision] = useState(0);
  const failure = useRef('Google sign-in is not configured yet.');
  useEffect(() => {
    let live = true;
    setReady(false);
    setLoading(true);
    setError('');
    async function initialize() {
      try {
        const config = await api<{ clientId: string }>('/auth/google/config');
        if (!live) return;
        if (!config.clientId) {
          failure.current = 'Google sign-in is not configured yet.';
          return;
        }
        await loadGoogle();
        if (!live || !root.current || !window.google) return;
        window.google.accounts.id.initialize({
          client_id: config.clientId,
          callback: async ({ credential }) => {
            if (busyRef.current || !live) return;
            busyRef.current = true;
            setBusy(true);
            setError('');
            try {
              const user = await api<User>('/auth/google', {
                method: 'POST',
                body: JSON.stringify({ credential }),
              });
              clearCsrfToken();
              await latestSignedIn.current(user);
            } catch (cause) {
              if (live) setError((cause as Error).message);
            } finally {
              busyRef.current = false;
              if (live) setBusy(false);
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
          locale: sv ? 'sv' : 'en',
        });
        setReady(true);
      } catch (cause) {
        failure.current = (cause as Error).message;
      } finally {
        if (live) setLoading(false);
      }
    }
    void initialize();
    return () => {
      live = false;
    };
  }, [language, darkMode, revision, sv]);
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
            if (failure.current === 'Google sign-in is not configured yet.')
              setError(failure.current);
            else {
              setError(failure.current);
              setRevision((value) => value + 1);
            }
          }}
        >
          <span aria-hidden="true">G</span>
          {loading
            ? sv
              ? 'Laddar Google…'
              : 'Loading Google…'
            : sv
              ? 'Fortsätt med Google'
              : 'Continue with Google'}
        </button>
      )}
      {busy && (
        <p role="status">
          {sv ? 'Loggar in med Google…' : 'Signing in with Google…'}
        </p>
      )}
      <Notice language={language} error={error} />
    </div>
  );
}
