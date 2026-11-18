import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { server } from '../test/server';
import { api, ApiError } from './client';

describe('REST client', () => {
  it('retains structured conflict extensions for actionable cycle feedback', async () => {
    server.use(
      http.get('*/api/projects/1/dependencies', () =>
        HttpResponse.json(
          { detail: 'Cycle', cycleKeys: ['SF-1', 'SF-2', 'SF-1'] },
          { status: 409 },
        ),
      ),
    );
    const cause = await api('/projects/1/dependencies').catch(
      (error: unknown) => error,
    );
    expect(cause).toBeInstanceOf(ApiError);
    expect((cause as ApiError).problem.cycleKeys).toEqual([
      'SF-1',
      'SF-2',
      'SF-1',
    ]);
  });

  it('handles a null error body and preserves successful arrays and primitives', async () => {
    server.use(
      http.get('*/api/null', () => HttpResponse.json(null, { status: 500 })),
      http.get('*/api/array', () => HttpResponse.json([1, 2])),
      http.get('*/api/count', () => HttpResponse.json(3)),
    );
    await expect(api('/null')).rejects.toBeInstanceOf(ApiError);
    await expect(api('/array')).resolves.toEqual([1, 2]);
    await expect(api('/count')).resolves.toBe(3);
  });
  afterEach(() => vi.restoreAllMocks());

  it('adds the server CSRF token to state-changing requests', async () => {
    let received: Request | undefined;
    server.use(
      http.get('*/api/auth/csrf', () =>
        HttpResponse.json({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN' }),
      ),
      http.post('*/api/projects', ({ request }) => {
        received = request;
        return HttpResponse.json({ id: 1 });
      }),
    );

    await api<{ id: number }>('/projects', {
      method: 'POST',
      body: JSON.stringify({ name: 'Secure project' }),
    });

    expect(new URL(received!.url).pathname).toBe('/api/projects');
    expect(received!.credentials).toBe('same-origin');
    expect(received!.headers.get('X-CSRF-TOKEN')).toBe('csrf-token');
    expect(received!.headers.get('Content-Type')).toBe('application/json');
  });

  it('keeps multipart content type unset so browser file uploads include their boundary', async () => {
    server.use(
      http.get('*/api/auth/csrf', () =>
        HttpResponse.json({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN' }),
      ),
      http.post('*/api/tasks/1/attachments/upload', () =>
        HttpResponse.json({ id: 1 }),
      ),
    );
    // Observe the client's own request options; MSW still answers the call.
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    const body = new FormData();
    body.append(
      'file',
      new Blob(['%PDF-test'], { type: 'application/pdf' }),
      'test.pdf',
    );

    await api('/tasks/1/attachments/upload', { method: 'POST', body });

    const [, request] = fetchSpy.mock.calls.at(-1) as [string, RequestInit];
    expect(new Headers(request.headers).has('Content-Type')).toBe(false);
    expect(new Headers(request.headers).get('X-CSRF-TOKEN')).toBe('csrf-token');
  });

  it('shows a useful explanation when the server returns an unstructured error', async () => {
    server.use(
      http.get('*/api/projects/1/activity', () =>
        HttpResponse.json({ error: 'Forbidden' }, { status: 403 }),
      ),
    );
    await expect(api('/projects/1/activity')).rejects.toThrow(
      'You don’t have permission to do that. Ask a project owner or admin for access.',
    );
  });

  it('explains connection failures without exposing browser error text', async () => {
    server.use(http.get('*/api/projects', () => HttpResponse.error()));
    await expect(api('/projects')).rejects.toThrow(
      'Could not connect to SprintFlow. Check your internet connection and try again.',
    );
  });
});
