import { fireEvent, screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, beforeAll, beforeEach, expect, it, vi } from 'vitest';
import { clearCsrfToken } from '../../api/client';
import { renderWithProviders } from '../../test/render';
import { AuthScreen } from './AuthScreen';

const user = {
  id: 7,
  email: 'account@example.test',
  displayName: 'Two Factor',
};
let csrf = 0;
const submit = vi.fn();
const primary = vi.fn();
const server = setupServer(
  http.get('/api/auth/csrf', () =>
    HttpResponse.json({ token: String(++csrf), headerName: 'X-CSRF-TOKEN' }),
  ),
  http.get('/api/auth/google/config', () =>
    HttpResponse.json({ clientId: '' }),
  ),
  http.post('/api/auth/login', ({ request }) => {
    primary(request.headers.get('X-CSRF-TOKEN'));
    return HttpResponse.json({ secondFactorRequired: true }, { status: 202 });
  }),
  http.post('/api/auth/two-factor/verify', async ({ request }) => {
    if (!request.bodyUsed)
      submit(await request.clone().json(), request.headers.get('X-CSRF-TOKEN'));
    return HttpResponse.json(user);
  }),
);

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
beforeEach(() => {
  csrf = 0;
  clearCsrfToken();
  submit.mockClear();
  primary.mockClear();
});
afterAll(() => server.close());

it('requests a fresh CSRF token and waits for two-factor verification before entering the workspace', async () => {
  const signedIn = vi.fn(async () => {});
  renderWithProviders(<AuthScreen initialError="" onSignedIn={signedIn} />);
  fireEvent.change(screen.getByLabelText('Email'), {
    target: { value: user.email },
  });
  fireEvent.change(screen.getByLabelText('Password'), {
    target: { value: 'fixture-password' },
  });
  fireEvent.click(screen.getByRole('button', { name: 'Log in' }));
  await screen.findByRole('heading', { name: 'Two-factor verification' });
  expect(signedIn).not.toHaveBeenCalled();
  expect(screen.queryByLabelText('Password')).not.toBeInTheDocument();
  fireEvent.change(screen.getByLabelText('Authentication code'), {
    target: { value: '123456' },
  });
  fireEvent.click(screen.getByRole('button', { name: 'Verify and sign in' }));
  await waitFor(() => expect(signedIn).toHaveBeenCalledWith(user));
  expect(primary).toHaveBeenCalled();
  expect(submit).toHaveBeenCalledWith({ code: '123456' }, expect.any(String));
  expect(submit.mock.lastCall?.[1]).not.toBe(primary.mock.lastCall?.[0]);
});
