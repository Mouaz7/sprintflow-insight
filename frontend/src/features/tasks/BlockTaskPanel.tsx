import type { FormEvent } from 'react';
import { useState } from 'react';
import { api } from '../../api/client';
import type { Language, Task } from '../../api/contracts';
import { localizeMessage } from '../../shared/i18n/messages';
import { FormGuide, fieldExample } from '../../shared/ui/Guidance';
import { Drawer } from '../../shared/ui/ui';

export function BlockTaskPanel({
  task,
  language,
  onClose,
  onSaved,
}: {
  task: Task;
  language: Language;
  onClose: () => void;
  onSaved: (task: Task) => void;
}) {
  const sv = language === 'SV';
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
      setError(
        cause instanceof Error
          ? cause.message
          : sv
            ? 'Uppgiften kunde inte blockeras.'
            : 'Could not block task.',
      );
    } finally {
      setBusy(false);
    }
  }
  return (
    <Drawer
      title={sv ? 'Blockera uppgift' : 'Block task'}
      language={language}
      onClose={onClose}
      dirty={!!reason}
      busy={busy}
    >
      <p>{task.title}</p>
      <form className="editor-form" onSubmit={submit}>
        <FormGuide topic="block" language={language} />
        <fieldset disabled={busy}>
          <label>
            {sv ? 'Orsak till blockering' : 'Block reason'}
            <textarea
              aria-label={sv ? 'Orsak till blockering' : 'Block reason'}
              required
              maxLength={500}
              placeholder={fieldExample('blockReason', language)}
              value={reason}
              onChange={(event) => setReason(event.target.value)}
            />
          </label>
        </fieldset>
        {error && (
          <p className="form-message error" role="alert">
            {localizeMessage(error, sv ? 'SV' : 'EN')}
          </p>
        )}
        <button className="primary-action" disabled={busy || !reason.trim()}>
          {busy
            ? sv
              ? 'Sparar…'
              : 'Saving…'
            : sv
              ? 'Blockera uppgift'
              : 'Block task'}
        </button>
      </form>
    </Drawer>
  );
}
