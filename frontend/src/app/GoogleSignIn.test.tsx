import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { api } from '../api/client';
import { GoogleSignIn } from './GoogleSignIn';
vi.mock('../api/client', () => ({ api: vi.fn(), clearCsrfToken: vi.fn() }));
describe('Google Identity integration', () => {
  beforeEach(() => vi.mocked(api).mockReset());
  afterEach(() => {
    delete window.google;
  });
  it('explains missing configuration without loading an external script', async () => {
    vi.mocked(api).mockResolvedValue({ clientId: '' });
    render(
      <GoogleSignIn language="SV" darkMode={false} onSignedIn={vi.fn()} />,
    );
    const button = await screen.findByRole('button', {
      name: 'Fortsätt med Google',
    });
    fireEvent.click(button);
    expect(screen.getByRole('alert')).toHaveTextContent(
      'Google-inloggning är inte konfigurerad ännu.',
    );
    expect(
      document.querySelector('script[src*="accounts.google.com"]'),
    ).toBeNull();
  });
  it('renders the official button and exchanges the credential for a server session', async () => {
    const initialize = vi.fn(),
      renderButton = vi.fn();
    window.google = { accounts: { id: { initialize, renderButton } } };
    const user = { id: 1, email: 'qa@example.com' };
    vi.mocked(api).mockImplementation(async (path) =>
      path === '/auth/google/config'
        ? { clientId: 'test.apps.googleusercontent.com' }
        : user,
    );
    const onSignedIn = vi.fn().mockResolvedValue(undefined);
    render(<GoogleSignIn language="EN" darkMode onSignedIn={onSignedIn} />);
    await waitFor(() => expect(renderButton).toHaveBeenCalled());
    expect(renderButton.mock.calls[0][1]).toMatchObject({
      theme: 'filled_black',
      locale: 'en',
      text: 'continue_with',
    });
    await act(async () => {
      await initialize.mock.calls[0][0].callback({
        credential: 'provider-token',
      });
    });
    expect(api).toHaveBeenCalledWith('/auth/google', {
      method: 'POST',
      body: JSON.stringify({ credential: 'provider-token' }),
    });
    expect(onSignedIn).toHaveBeenCalledWith(user);
  });
  it('shows a rejected token error and prevents concurrent credential submissions', async () => {
    const initialize = vi.fn();
    window.google = { accounts: { id: { initialize, renderButton: vi.fn() } } };
    vi.mocked(api)
      .mockResolvedValueOnce({ clientId: 'test.apps.googleusercontent.com' })
      .mockRejectedValueOnce(
        new Error('Google could not verify this account.'),
      );
    render(
      <GoogleSignIn language="EN" darkMode={false} onSignedIn={vi.fn()} />,
    );
    await waitFor(() => expect(initialize).toHaveBeenCalled());
    const callback = initialize.mock.calls[0][0].callback;
    await act(async () => {
      await Promise.all([
        callback({ credential: 'invalid' }),
        callback({ credential: 'invalid' }),
      ]);
    });
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Google could not verify',
    );
    expect(
      vi.mocked(api).mock.calls.filter(([path]) => path === '/auth/google'),
    ).toHaveLength(1);
  });
});
