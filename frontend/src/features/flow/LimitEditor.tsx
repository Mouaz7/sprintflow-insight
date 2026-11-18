import { useState, type FormEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import type { WipSettings } from '../../api/contracts';
import { ApiError } from '../../api/client';
import { wipLimitsQuery } from '../../api/queries';
import { useT } from '../../shared/i18n/LanguageProvider';
import { Drawer, Notice } from '../../shared/ui/ui';
import { ErrorState, Skeleton } from '../../shared/ui/States';
import { FormGuide } from '../../shared/ui/Guidance';
import { columnName, flowText } from './i18n';
import { useSaveWipLimits } from './queries';

export function LimitEditor({
  projectId,
  onClose,
}: {
  projectId: number;
  onClose: () => void;
}) {
  const t = useT(flowText);
  const query = useQuery(wipLimitsQuery(projectId));
  const mutation = useSaveWipLimits(projectId);
  const [dirty, setDirty] = useState(false);
  const cause = mutation.error;
  const error = !cause
    ? ''
    : cause instanceof ApiError && [412, 428].includes(cause.status)
      ? t('stale')
      : cause instanceof ApiError && [401, 403, 409].includes(cause.status)
        ? t('denied')
        : cause instanceof ApiError && cause.status === 400
          ? t('invalid')
          : t('saveFailed');
  async function save(draft: WipSettings) {
    try {
      await mutation.mutateAsync(draft);
      onClose();
    } catch {
      /* Keep the draft. */
    }
  }
  // One dialog survives loading/refetching so its focus and any draft stay in place.
  return (
    <Drawer
      title={t('limitsTitle')}
      onClose={onClose}
      dirty={dirty}
      busy={mutation.isPending}
    >
      {query.data ? (
        <>
          <FormGuide topic="wipLimits" />
          <Notice error={error} />
          <LimitForm
            initial={query.data}
            busy={mutation.isPending}
            onDirty={() => setDirty(true)}
            save={save}
          />
        </>
      ) : query.isError ? (
        <ErrorState detail={t('failed')} onRetry={() => void query.refetch()} />
      ) : (
        <Skeleton label={t('loading')} />
      )}
    </Drawer>
  );
}

function LimitForm({
  initial,
  busy,
  onDirty,
  save,
}: {
  initial: WipSettings;
  busy: boolean;
  onDirty: () => void;
  save: (draft: WipSettings) => Promise<void>;
}) {
  const t = useT(flowText);
  const [revision] = useState(initial.version);
  const [values, setValues] = useState(() =>
    (['To Do', 'In Progress', 'Review'] as const).map((status) => ({
      status,
      value: String(
        initial.limits.find((row) => row.status === status)?.limit ?? '',
      ),
    })),
  );
  function submit(event: FormEvent) {
    event.preventDefault();
    void save({
      version: revision,
      limits: values.map((row) => ({
        status: row.status,
        limit: row.value === '' ? null : Number(row.value),
      })),
    });
  }
  return (
    <form className="editor-form" onSubmit={submit}>
      <p>{t('limitHelp')}</p>
      {values.map((row, index) => (
        <label key={row.status}>
          {columnName(t, row.status)}
          <input
            type="number"
            min="1"
            max="999"
            step="1"
            value={row.value}
            placeholder={t('noLimit')}
            onChange={(event) => {
              onDirty();
              setValues(
                values.map((item, i) =>
                  i === index ? { ...item, value: event.target.value } : item,
                ),
              );
            }}
          />
        </label>
      ))}
      <button className="primary-action" disabled={busy}>
        {t(busy ? 'saving' : 'save')}
      </button>
    </form>
  );
}
