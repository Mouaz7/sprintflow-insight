import { useState } from 'react';
import {
  queryOptions,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query';
import { api } from '../../api/client';
import { useT } from '../../shared/i18n/LanguageProvider';
import { Notice } from '../../shared/ui/ui';
import { ErrorState, Skeleton } from '../../shared/ui/States';
import { digestText } from './digestText';
export type DigestPreferences = {
  frequency: 'OFF' | 'DAILY' | 'WEEKLY';
  timeZone: string;
  version: number;
  nextDueAt: string | null;
  lastSentAt: string | null;
  mailConfigured: boolean;
};
export const digestQuery = queryOptions({
  queryKey: ['digest-preferences'],
  queryFn: ({ signal }) => api<DigestPreferences>('/auth/digest', { signal }),
});
export function DigestSettings() {
  const t = useT(digestText);
  const client = useQueryClient();
  const query = useQuery(digestQuery);
  const [draft, setDraft] = useState<DigestPreferences>();
  const [saved, setSaved] = useState(false);
  const mutation = useMutation({
    mutationFn: (value: DigestPreferences) =>
      api<DigestPreferences>('/auth/digest', {
        method: 'PUT',
        headers: { 'If-Match': `"${value.version}"` },
        body: JSON.stringify({
          frequency: value.frequency,
          timeZone: value.timeZone,
        }),
      }),
    onSuccess: (value) => {
      client.setQueryData(digestQuery.queryKey, value);
      setDraft(undefined);
      setSaved(true);
    },
    onError: async () => {
      await client.invalidateQueries({ queryKey: digestQuery.queryKey });
    },
  });
  if (query.isError)
    return (
      <ErrorState detail={t('failed')} onRetry={() => void query.refetch()} />
    );
  if (!query.data) return <Skeleton label={t('loading')} />;
  const value = draft ?? query.data;
  const conflict =
    !!mutation.error &&
    'status' in mutation.error &&
    [412, 428].includes(Number(mutation.error.status));
  return (
    <form
      className="profile-card digest-settings"
      onSubmit={(e) => {
        e.preventDefault();
        setSaved(false);
        mutation.mutate(value);
      }}
    >
      <h2>{t('title')}</h2>
      <p>{t('help')}</p>
      {!query.data.mailConfigured && <p>{t('notConfigured')}</p>}
      <fieldset disabled={mutation.isPending}>
        <label>
          {t('frequency')}
          <select
            value={value.frequency}
            onChange={(e) => {
              setSaved(false);
              setDraft({
                ...value,
                frequency: e.target.value as DigestPreferences['frequency'],
              });
            }}
          >
            {(['OFF', 'DAILY', 'WEEKLY'] as const).map((key) => (
              <option key={key} value={key}>
                {t(key)}
              </option>
            ))}
          </select>
        </label>
        <label>
          {t('zone')}
          <input
            value={value.timeZone}
            maxLength={64}
            required
            list="digest-time-zones"
            onChange={(e) => {
              setSaved(false);
              setDraft({ ...value, timeZone: e.target.value });
            }}
          />
        </label>
        <datalist id="digest-time-zones">
          {[
            'UTC',
            'Europe/Stockholm',
            'Europe/London',
            'America/New_York',
            'Asia/Riyadh',
            'Asia/Tokyo',
            'Australia/Sydney',
          ].map((zone) => (
            <option key={zone} value={zone} />
          ))}
        </datalist>
        <button className="primary-action" disabled={conflict}>
          {t(mutation.isPending ? 'saving' : 'save')}
        </button>
      </fieldset>
      <Notice
        error={
          mutation.error ? t(conflict ? 'conflict' : 'saveFailed') : undefined
        }
        success={saved ? t('saved') : undefined}
      />
      {conflict && (
        <button
          type="button"
          className="secondary-action"
          onClick={async () => {
            const latest = await query.refetch();
            if (latest.isSuccess) {
              setDraft(undefined);
              mutation.reset();
            }
          }}
        >
          {t('reload')}
        </button>
      )}
      {query.data.nextDueAt && (
        <p>
          {t('next')}:{' '}
          {t('date', {
            value: query.data.nextDueAt,
            zone: query.data.timeZone,
          })}
        </p>
      )}
      {query.data.lastSentAt && (
        <p>
          {t('last')}:{' '}
          {t('date', {
            value: query.data.lastSentAt,
            zone: query.data.timeZone,
          })}
        </p>
      )}
    </form>
  );
}
