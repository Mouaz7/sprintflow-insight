import { localizeMessage } from './messages';

export function SettingsIcon({ kind }: { kind: 'person' | 'lock' | 'logout' }) {
  return (
    <svg
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {kind === 'person' ? (
        <>
          <circle cx="12" cy="8" r="3.5" />
          <path d="M5 21v-2a7 7 0 0 1 14 0v2" />
        </>
      ) : kind === 'lock' ? (
        <>
          <rect x="5" y="10" width="14" height="11" rx="2" />
          <path d="M8 10V7a4 4 0 0 1 8 0v3M12 14v3" />
        </>
      ) : (
        <>
          <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M9 12h12m-4-4 4 4-4 4" />
        </>
      )}
    </svg>
  );
}

export function Feedback({
  error,
  message,
  sv,
}: {
  error: string;
  message?: string;
  sv: boolean;
}) {
  if (!error && !message) return null;
  return (
    <p
      className={'form-message ' + (error ? 'error' : 'success')}
      role={error ? 'alert' : 'status'}
    >
      {error ? localizeMessage(error, sv ? 'SV' : 'EN') : message}
    </p>
  );
}
