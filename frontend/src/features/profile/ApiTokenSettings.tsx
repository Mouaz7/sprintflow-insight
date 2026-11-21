import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';
import { useLanguage, useT } from '../../shared/i18n/LanguageProvider';
import { localizeMessage } from '../../shared/i18n/messages';
import { Notice, useConfirm } from '../../shared/ui/ui';

const scopes = ['PROJECT_READ', 'TASK_READ', 'TASK_WRITE'] as const;
type Scope = (typeof scopes)[number];
type Token = {
  id: number;
  label: string;
  scopes: Scope[];
  expiresAt: string;
  lastUsedAt: string | null;
};
type TokenDraft = { label: string; scopes: Scope[]; expiresInDays: number };
type IssuedToken = { metadata: Token; token: string };

const path = '/auth/tokens';
const queryKey = ['account', 'personal-access-tokens'] as const;

const text = {
  EN: {
    title: 'Personal access tokens',
    docs: 'OpenAPI documentation',
    label: 'Token name',
    duration: 'Expires in days',
    permissions: 'API permissions',
    PROJECT_READ: 'Read projects',
    TASK_READ: 'Read tasks',
    TASK_WRITE: 'Create tasks',
    create: 'Create access token',
    secret: 'New access token',
    showOnce: 'Copy this token. It is shown once.',
    hide: 'Hide token',
    expires: 'Expires',
    lastUsed: 'Last used',
    never: 'Never',
    empty: 'No access tokens yet.',
    revoke: 'Revoke token',
    confirm: 'Revoke this access token?',
  },
  SV: {
    title: 'Personliga åtkomsttoken',
    docs: 'OpenAPI-dokumentation',
    label: 'Tokennamn',
    duration: 'Giltighet i dagar',
    permissions: 'API-behörigheter',
    PROJECT_READ: 'Läsa projekt',
    TASK_READ: 'Läsa uppgifter',
    TASK_WRITE: 'Skapa uppgifter',
    create: 'Skapa åtkomsttoken',
    secret: 'Nytt åtkomsttoken',
    showOnce: 'Kopiera tokenvärdet. Det visas en gång.',
    hide: 'Dölj token',
    expires: 'Upphör',
    lastUsed: 'Senast använd',
    never: 'Aldrig',
    empty: 'Inga åtkomsttoken än.',
    revoke: 'Återkalla token',
    confirm: 'Återkalla detta åtkomsttoken?',
  },
} as const;

export default function ApiTokenSettings() {
  const t = useT(text);
  const { language, format } = useLanguage();
  const confirm = useConfirm();
  const client = useQueryClient();
  const [expanded, setExpanded] = useState(false);
  const [selected, setSelected] = useState<Scope[]>([]);
  const [issued, setIssued] = useState<string>();
  const list = useQuery({
    queryKey,
    queryFn: () => api<Token[]>(path),
    enabled: expanded,
  });
  const refresh = () => client.invalidateQueries({ queryKey });
  const create = useMutation({
    mutationFn: (draft: TokenDraft) =>
      api<IssuedToken>(path, { method: 'POST', body: JSON.stringify(draft) }),
    onSuccess: (result) => {
      setIssued(result.token);
      setSelected([]);
      return refresh();
    },
    gcTime: 0,
  });
  const revoke = useMutation({
    mutationFn: (id: number) => api(`${path}/${id}`, { method: 'DELETE' }),
    onSuccess: refresh,
  });
  const error = create.error ?? revoke.error ?? list.error;
  return (
    <details
      className="profile-card"
      id="api-tokens"
      onToggle={(event) => setExpanded(event.currentTarget.open)}
    >
      <summary>{t('title')}</summary>
      <a href="/api/v1/openapi" target="_blank" rel="noopener noreferrer">
        {t('docs')}
      </a>
      {error && <Notice error={localizeMessage(error.message, language)} />}
      {issued ? (
        <div>
          <p role="status">{t('showOnce')}</p>
          <label>
            {t('secret')}
            <input readOnly autoComplete="off" value={issued} />
          </label>
          <button
            type="button"
            onClick={() => {
              setIssued(undefined);
              create.reset();
            }}
          >
            {t('hide')}
          </button>
        </div>
      ) : (
        <form
          className="editor-form"
          onSubmit={(event) => {
            event.preventDefault();
            const values = new FormData(event.currentTarget);
            if (selected.length)
              create.mutate({
                label: String(values.get('label') ?? ''),
                expiresInDays: Number(values.get('expires')),
                scopes: selected,
              });
          }}
        >
          <label>
            {t('label')}
            <input name="label" required maxLength={80} />
          </label>
          <label>
            {t('duration')}
            <input
              name="expires"
              type="number"
              required
              min={1}
              max={90}
              defaultValue={30}
            />
          </label>
          <fieldset disabled={create.isPending}>
            <legend>{t('permissions')}</legend>
            {scopes.map((scope) => (
              <label key={scope}>
                <input
                  type="checkbox"
                  checked={selected.includes(scope)}
                  onChange={(event) => {
                    const checked = event.target.checked;
                    setSelected((previous) =>
                      checked
                        ? [...previous, scope]
                        : previous.filter((item) => item !== scope),
                    );
                  }}
                />{' '}
                {t(scope)}
              </label>
            ))}
          </fieldset>
          <button
            className="primary-action"
            disabled={create.isPending || !selected.length}
          >
            {t('create')}
          </button>
        </form>
      )}
      {list.data?.length === 0 && <p>{t('empty')}</p>}
      <ul>
        {list.data?.map((token) => (
          <li key={token.id}>
            <strong>{token.label}</strong>
            <p>{token.scopes.map((scope) => t(scope)).join(', ')}</p>
            <p>
              {t('expires')} {format.dateTime(token.expiresAt)}
            </p>
            <p>
              {t('lastUsed')}{' '}
              {token.lastUsedAt
                ? format.dateTime(token.lastUsedAt)
                : t('never')}
            </p>
            <button
              type="button"
              className="danger-action"
              disabled={revoke.isPending}
              onClick={async () => {
                if (await confirm(t('confirm'))) revoke.mutate(token.id);
              }}
            >
              {t('revoke')}
            </button>
          </li>
        ))}
      </ul>
    </details>
  );
}
