import { fireEvent, screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, beforeAll, expect, it } from 'vitest';
import { clearCsrfToken } from '../../api/client';
import { renderWithProviders } from '../../test/render';
import ApiTokenSettings from './ApiTokenSettings';

const server = setupServer();
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterAll(() => server.close());

it('issues only chosen scopes and clears the one-time token after hiding it', async () => {
  let metadata:
    | {
        id: number;
        label: string;
        scopes: string[];
        expiresAt: string;
        lastUsedAt: null;
      }
    | undefined;
  clearCsrfToken();
  server.use(
    http.get('/api/auth/csrf', () =>
      HttpResponse.json({ token: 'fixture', headerName: 'X-CSRF-TOKEN' }),
    ),
    http.get('/api/auth/tokens', () =>
      HttpResponse.json(metadata ? [metadata] : []),
    ),
    http.post('/api/auth/tokens', async ({ request }) => {
      const draft = (await request.json()) as {
        label: string;
        scopes: string[];
        expiresInDays: number;
      };
      expect(draft).toEqual({
        label: 'Build agent',
        scopes: ['TASK_READ'],
        expiresInDays: 30,
      });
      metadata = {
        id: 1,
        label: draft.label,
        scopes: draft.scopes,
        expiresAt: '2026-12-21T08:00:00Z',
        lastUsedAt: null,
      };
      return HttpResponse.json(
        { metadata, token: 'sf_pat_disposable_fixture' },
        { status: 201 },
      );
    }),
  );
  renderWithProviders(<ApiTokenSettings />);
  fireEvent.click(screen.getByText('Personal access tokens', { exact: true }));
  await screen.findByText('No access tokens yet.');
  fireEvent.change(screen.getByLabelText('Token name'), {
    target: { value: 'Build agent' },
  });
  fireEvent.click(screen.getByRole('checkbox', { name: 'Read tasks' }));
  fireEvent.click(screen.getByRole('button', { name: 'Create access token' }));
  expect(await screen.findByLabelText('New access token')).toHaveValue(
    'sf_pat_disposable_fixture',
  );
  fireEvent.click(screen.getByRole('button', { name: 'Hide token' }));
  expect(screen.queryByLabelText('New access token')).not.toBeInTheDocument();
  expect(await screen.findByText('Build agent')).toBeVisible();
});
