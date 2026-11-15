import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { api } from '../../api/client';
import { FormGuide } from '../../shared/ui/Guidance';
import { localizeMessage } from '../../shared/i18n/messages';

type Attachment = { id: number; name: string; url: string; createdAt: string };
export function TaskAttachments({
  taskId,
  language,
}: {
  taskId: number;
  language: 'EN' | 'SV';
}) {
  const sv = language === 'SV';
  const [items, setItems] = useState<Attachment[]>([]);
  const [url, setUrl] = useState('');
  const [file, setFile] = useState<File>();
  const [message, setMessage] = useState('');
  async function refresh() {
    setItems(await api<Attachment[]>(`/tasks/${taskId}/attachments`));
  }
  useEffect(() => {
    let cancelled = false;
    setItems([]);
    setMessage('');
    api<Attachment[]>(`/tasks/${taskId}/attachments`)
      .then((attachments) => {
        if (!cancelled) setItems(attachments);
      })
      .catch((cause: Error) => {
        if (!cancelled) setMessage(cause.message);
      });
    return () => {
      cancelled = true;
    };
  }, [taskId]);
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
