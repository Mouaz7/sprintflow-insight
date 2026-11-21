import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { api } from '../../api/client';
import { useLanguage, useT } from '../../shared/i18n/LanguageProvider';
import { localizeMessage } from '../../shared/i18n/messages';
import { createPasskey, usePasskeysAvailable } from '../../shared/lib/passkeys';
import { Notice, useConfirm } from '../../shared/ui/ui';
import { profileText } from './i18n';

type Passkey = {
  id: string;
  label: string;
  created: string;
  lastUsed: string | null;
};
const key = ['account', 'passkeys'] as const;

export default function PasskeySettings() {
  const t = useT(profileText);
  const { language, format } = useLanguage();
  const confirm = useConfirm();
  const client = useQueryClient();
  const available = usePasskeysAvailable();
  const [label, setLabel] = useState('');
  const list = useQuery({
    queryKey: key,
    queryFn: () => api<Passkey[]>('/auth/passkeys'),
    enabled: !!available,
  });
  const register = useMutation({
    mutationFn: createPasskey,
    onSuccess: async () => {
      setLabel('');
      await client.invalidateQueries({ queryKey: key });
    },
  });
  const remove = useMutation({
    mutationFn: (id: string) =>
      api(`/auth/passkeys/${encodeURIComponent(id)}`, { method: 'DELETE' }),
    onSuccess: () => client.invalidateQueries({ queryKey: key }),
  });
  function submit(event: FormEvent) {
    event.preventDefault();
    if (label.trim()) register.mutate(label.trim());
  }
  if (!available) return null;
  return (
    <section className="profile-card" id="passkeys">
      <h2>{t('passkeys')}</h2>
      <Notice
        error={localizeMessage(
          list.error?.message ??
            register.error?.message ??
            remove.error?.message ??
            '',
          language,
        )}
      />
      <form className="editor-form" onSubmit={submit}>
        <label>
          {t('passkeyName')}
          <input
            value={label}
            required
            maxLength={80}
            onChange={(event) => setLabel(event.target.value)}
          />
        </label>
        <button
          className="primary-action"
          disabled={register.isPending || !label.trim()}
        >
          {t('addPasskey')}
        </button>
      </form>
      {list.data?.length === 0 && <p>{t('noPasskeys')}</p>}
      <ul>
        {list.data?.map((item) => (
          <li key={item.id}>
            <strong>{item.label}</strong>
            <p>
              {t('passkeyAdded')} {format.dateTime(item.created)}
            </p>
            {item.lastUsed && (
              <p>
                {t('passkeyUsed')} {format.dateTime(item.lastUsed)}
              </p>
            )}
            <button
              className="danger-action"
              disabled={remove.isPending}
              type="button"
              onClick={async () => {
                if (await confirm(t('confirmRemovePasskey')))
                  remove.mutate(item.id);
              }}
            >
              {t('removePasskey')}
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}
