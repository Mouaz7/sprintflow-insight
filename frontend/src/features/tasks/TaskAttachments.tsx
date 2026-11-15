import { useQuery, useQueryClient } from '@tanstack/react-query';
import type { FormEvent } from 'react';
import { useState } from 'react';
import { api } from '../../api/client';
import { localizeMessage } from '../../shared/i18n/messages';
import { FormGuide } from '../../shared/ui/Guidance';
import { useLanguage, useT } from '../../shared/i18n/LanguageProvider';
import { tasksText } from './i18n';
import { attachmentsQuery } from './queries';
export function TaskAttachments({ taskId }: { taskId: number }) {
  const { language } = useLanguage();
  const t = useT(tasksText);
  const queryClient = useQueryClient();
  const attachments = useQuery(attachmentsQuery(taskId));
  const items = attachments.data ?? [];
  const [url, setUrl] = useState('');
  const [file, setFile] = useState<File>();
  // Undefined shows the load error, if any; actions set or clear their own.
  const [actionMessage, setMessage] = useState<string>();
  const message = actionMessage ?? attachments.error?.message ?? '';
  async function refresh() {
    await queryClient.refetchQueries({
      queryKey: attachmentsQuery(taskId).queryKey,
    });
  }
  async function addLink(event: FormEvent) {
    event.preventDefault();
    setMessage('');
    try {
      await api(`/tasks/${taskId}/attachments`, {
        method: 'POST',
        body: JSON.stringify({ url, name: url }),
      });
      setUrl('');
      await refresh();
    } catch (cause) {
      setMessage(
        cause instanceof Error ? cause.message : 'Could not add link.',
      );
    }
  }
  async function addFile(event: FormEvent) {
    event.preventDefault();
    if (!file) return;
    const data = new FormData();
    data.append('file', file);
    setMessage('');
    try {
      await api(`/tasks/${taskId}/attachments/upload`, {
        method: 'POST',
        body: data,
      });
      setFile(undefined);
      await refresh();
    } catch (cause) {
      setMessage(
        cause instanceof Error ? cause.message : 'Could not upload file.',
      );
    }
  }
  async function remove(id: number) {
    try {
      await api(`/tasks/${taskId}/attachments/${id}`, { method: 'DELETE' });
      await refresh();
    } catch (cause) {
      setMessage(
        cause instanceof Error ? cause.message : 'Could not remove attachment.',
      );
    }
  }
  return (
    <details className="task-attachments">
      <summary>
        {t('attachments')} ({items.length})
      </summary>
      <ul>
        {items.map((item) => (
          <li key={item.id}>
            <a href={item.url} target="_blank" rel="noreferrer">
              {item.name}
            </a>
            <button
              type="button"
              className="danger-action"
              onClick={() => remove(item.id)}
            >
              {t('remove')}
            </button>
          </li>
        ))}
      </ul>
      <FormGuide topic="attachment" />
      <form onSubmit={addLink}>
        <label>
          {t('addLink')}
          <input
            type="url"
            required
            value={url}
            onChange={(event) => setUrl(event.target.value)}
            placeholder="https://example.com/login-design.pdf"
          />
        </label>
        <button>{t('saveLink')}</button>
      </form>
      <form onSubmit={addFile}>
        <label>
          {t('uploadFile')}
          <input
            type="file"
            required
            accept="image/jpeg,image/png,image/gif,image/webp,application/pdf"
            onChange={(event) => setFile(event.target.files?.[0])}
          />
        </label>
        <button disabled={!file}>{t('upload')}</button>
      </form>
      {message && (
        <p className="form-message error" role="alert">
          {localizeMessage(message, language)}
        </p>
      )}
    </details>
  );
}
