import { useQuery, useQueryClient } from '@tanstack/react-query';
import type { FormEvent } from 'react';
import { useState } from 'react';
import { api } from '../../api/client';
import { localizeMessage } from '../../shared/i18n/messages';
import { FormGuide } from '../../shared/ui/Guidance';
import { attachmentsQuery } from './queries';
export function TaskAttachments({
  taskId,
  language,
}: {
  taskId: number;
  language: 'EN' | 'SV';
}) {
  const sv = language === 'SV';
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
        {sv ? 'Bilagor och länkar' : 'Attachments and links'} ({items.length})
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
              {sv ? 'Ta bort' : 'Remove'}
            </button>
          </li>
        ))}
      </ul>
      <FormGuide topic="attachment" language={language} />
      <form onSubmit={addLink}>
        <label>
          {sv ? 'Lägg till länk' : 'Add link'}
          <input
            type="url"
            required
            value={url}
            onChange={(event) => setUrl(event.target.value)}
            placeholder="https://example.com/login-design.pdf"
          />
        </label>
        <button>{sv ? 'Spara länk' : 'Save link'}</button>
      </form>
      <form onSubmit={addFile}>
        <label>
          {sv ? 'Ladda upp fil' : 'Upload file'}
          <input
            type="file"
            required
            accept="image/jpeg,image/png,image/gif,image/webp,application/pdf"
            onChange={(event) => setFile(event.target.files?.[0])}
          />
        </label>
        <button disabled={!file}>{sv ? 'Ladda upp' : 'Upload'}</button>
      </form>
      {message && (
        <p className="form-message error" role="alert">
          {localizeMessage(message, sv ? 'SV' : 'EN')}
        </p>
      )}
    </details>
  );
}
