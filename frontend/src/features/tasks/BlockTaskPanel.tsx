import type { FormEvent } from 'react';
import { useState } from 'react';
import { api } from '../../api/client';
import type { Task } from '../../api/contracts';
import { useLanguage, useT } from '../../shared/i18n/LanguageProvider';
import { tasksText } from './i18n';
import { localizeMessage } from '../../shared/i18n/messages';
import { FormGuide, useFieldExample } from '../../shared/ui/Guidance';
import { Drawer } from '../../shared/ui/ui';

export function BlockTaskPanel({
  task,
  onClose,
  onSaved,
}: {
  task: Task;
  onClose: () => void;
  onSaved: (task: Task) => void;
}) {
  const { language } = useLanguage();
  const t = useT(tasksText);
  const fieldExample = useFieldExample();
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (busy || !reason.trim()) return;
    setBusy(true);
    setError('');
    try {
      onSaved(
        await api<Task>(
          `/tasks/${task.id}/block?reason=${encodeURIComponent(reason.trim())}`,
          { method: 'PUT', headers: { 'If-Match': `"${task.version}"` } },
        ),
      );
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t('blockFailed'));
    } finally {
      setBusy(false);
    }
  }
  return (
    <Drawer
      title={t('blockTask')}
      onClose={onClose}
      dirty={!!reason}
      busy={busy}
    >
      <p>{task.title}</p>
      <form className="editor-form" onSubmit={submit}>
        <FormGuide topic="block" />
        <fieldset disabled={busy}>
          <label>
            {t('blockReason')}
            <textarea
              aria-label={t('blockReason')}
              required
              maxLength={500}
              placeholder={fieldExample('blockReason')}
              value={reason}
              onChange={(event) => setReason(event.target.value)}
            />
          </label>
        </fieldset>
        {error && (
          <p className="form-message error" role="alert">
            {localizeMessage(error, language)}
          </p>
        )}
        <button className="primary-action" disabled={busy || !reason.trim()}>
          {busy ? t('saving') : t('blockTask')}
        </button>
      </form>
    </Drawer>
  );
}
