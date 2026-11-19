import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router';
import { api, ApiError } from '../../api/client';
import { useLanguage, useT } from '../../shared/i18n/LanguageProvider';
import { Notice } from '../../shared/ui/ui';
import { digestText } from './digestText';
export function UnsubscribeView() {
  const t = useT(digestText);
  const client = useQueryClient();
  const { language, setLanguage } = useLanguage();
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState<'invalid' | 'unsubscribeFailed'>();
  return (
    <main className="unsubscribe-page">
      <section className="profile-card">
        <label>
          {t('language')}
          <select
            value={language}
            onChange={(e) => setLanguage(e.target.value as 'EN' | 'SV')}
          >
            <option value="EN">English</option>
            <option value="SV">Svenska</option>
          </select>
        </label>
        <h1>{t('unsubscribe')}</h1>
        <p>{t('unsubscribeHelp')}</p>
        {!token || token.length > 2048 ? (
          <p role="alert">{t('invalid')}</p>
        ) : done ? (
          <p role="status">{t('stopped')}</p>
        ) : (
          <button
            className="primary-action"
            disabled={busy}
            onClick={async () => {
              setBusy(true);
              setError(undefined);
              try {
                await api('/digest/unsubscribe', {
                  method: 'POST',
                  body: JSON.stringify({ token }),
                });
                await client.invalidateQueries({
                  queryKey: ['digest-preferences'],
                });
                setDone(true);
              } catch (cause) {
                setError(
                  cause instanceof ApiError && cause.status === 400
                    ? 'invalid'
                    : 'unsubscribeFailed',
                );
              } finally {
                setBusy(false);
              }
            }}
          >
            {t(busy ? 'saving' : 'confirm')}
          </button>
        )}
        <Notice error={error ? t(error) : undefined} />
        <Link to="/profile">{t('profile')}</Link>
      </section>
    </main>
  );
}
