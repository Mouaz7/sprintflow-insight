import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from './client';

describe('REST client', () => {
  beforeEach(() => vi.restoreAllMocks());

  it('adds the server CSRF token to state-changing requests', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => ({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN' }) })
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => ({ id: 1 }) });
    vi.stubGlobal('fetch', fetchMock);

    await api<{ id: number }>('/projects', { method: 'POST', body: JSON.stringify({ name: 'Secure project' }) });

    const [url, request] = fetchMock.mock.calls.at(-1) as [string, RequestInit];
    expect(url).toBe('/api/projects');
    expect(request.credentials).toBe('same-origin');
    expect(new Headers(request.headers).get('X-CSRF-TOKEN')).toBe('csrf-token');
  });

  it('keeps multipart content type unset so browser file uploads include their boundary', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => ({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN' }) })
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => ({ id: 1 }) });
    vi.stubGlobal('fetch', fetchMock);
    const body = new FormData();
    body.append('file', new Blob(['%PDF-test'], { type: 'application/pdf' }), 'test.pdf');

    await api('/tasks/1/attachments/upload', { method: 'POST', body });

    const [, request] = fetchMock.mock.calls.at(-1) as [string, RequestInit];
    expect(new Headers(request.headers).has('Content-Type')).toBe(false);
    expect(new Headers(request.headers).get('X-CSRF-TOKEN')).toBe('csrf-token');
  });

  it('shows a useful explanation when the server returns an unstructured error', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 403, json: async () => ({ error: 'Forbidden' }) }));
    await expect(api('/projects/1/activity')).rejects.toThrow('You don’t have permission to do that. Ask a project owner or admin for access.');
  });

  it('explains connection failures without exposing browser error text', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    await expect(api('/projects')).rejects.toThrow('Could not connect to SprintFlow. Check your internet connection and try again.');
  });
});
