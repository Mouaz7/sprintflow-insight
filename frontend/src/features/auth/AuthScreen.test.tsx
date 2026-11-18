import { fireEvent, screen } from '@testing-library/react';
import { useLocation } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError } from '../../api/client';
import { renderWithProviders } from '../../test/render';
import { AuthScreen } from './AuthScreen';

const resetLink = '/reset-password?token=test-token';

function CurrentLocation() {
  const location = useLocation();
  return <p data-testid="location">{location.pathname + location.search}</p>;
}

function renderAuth(path = resetLink) {
  return renderWithProviders(
    <>
      <AuthScreen initialError="" onSignedIn={vi.fn()} />
      <CurrentLocation />
    </>,
    { path },
  );
}

vi.mock('../../api/client', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/client')>()),
  api: vi.fn(),
}));
describe('Account recovery', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });
  it('switches an existing sign-in error and theme controls between English and Swedish', async () => {
    vi.mocked(api).mockRejectedValue(
      new ApiError('Incorrect email or password.', 401),
    );
    renderAuth('/');
    fireEvent.change(screen.getByLabelText('Email'), {
      target: { value: 'missing@example.com' },
    });
    fireEvent.change(screen.getByLabelText('Password'), {
      target: { value: 'wrong-password' },
    });
    fireEvent.click(screen.getAllByRole('button', { name: 'Log in' }).at(-1)!);
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'If you haven’t created an account yet',
    );
    fireEvent.change(screen.getByRole('combobox', { name: 'Language' }), {
      target: { value: 'SV' },
    });
    expect(screen.getByRole('alert')).toHaveTextContent(
      'Om du inte har ett konto ännu',
    );
    expect(
      screen.getByRole('button', { name: 'Byt till mörkt läge' }),
    ).toBeInTheDocument();
    fireEvent.change(screen.getByRole('combobox', { name: 'Språk' }), {
      target: { value: 'EN' },
    });
    expect(screen.getByRole('alert')).toHaveTextContent(
      'If you haven’t created an account yet',
    );
    expect(
      screen.getByRole('button', { name: 'Switch to dark mode' }),
    ).toBeInTheDocument();
  });
  it('checks password confirmation before sending a reset and shows success', async () => {
    vi.mocked(api).mockResolvedValue({});
    renderAuth();
    expect(
      screen.getByRole('heading', { name: 'Choose a new password.' }),
    ).toBeInTheDocument();
    expect(screen.queryByLabelText('Email')).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Password'), {
      target: { value: 'reset-password-123' },
    });
    fireEvent.change(screen.getByLabelText('Confirm new password'), {
      target: { value: 'different-password' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Save new password' }));
    expect(screen.getByRole('alert')).toHaveTextContent('do not match');
    expect(api).not.toHaveBeenCalled();
    fireEvent.change(screen.getByLabelText('Confirm new password'), {
      target: { value: 'reset-password-123' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Save new password' }));
    expect(await screen.findByRole('status')).toHaveTextContent(
      'Password updated. You can now log in.',
    );
    expect(api).toHaveBeenCalledWith('/auth/password/reset/confirm', {
      method: 'POST',
      body: JSON.stringify({
        token: 'test-token',
        newPassword: 'reset-password-123',
      }),
    });
    fireEvent.click(screen.getByRole('button', { name: /Back to log in/ }));
    expect(screen.getByTestId('location')).toHaveTextContent(/^\/$/);
    expect(
      screen.getByRole('heading', { name: 'Welcome back.' }),
    ).toBeInTheDocument();
  });
  it('retains passwords and displays the server error for an expired link', async () => {
    vi.mocked(api).mockRejectedValue(
      new Error('This reset link is invalid or has expired.'),
    );
    renderAuth();
    for (const label of ['Password', 'Confirm new password'])
      fireEvent.change(screen.getByLabelText(label), {
        target: { value: 'reset-password-123' },
      });
    fireEvent.click(screen.getByRole('button', { name: 'Save new password' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'invalid or has expired',
    );
    expect(screen.getByLabelText('Password')).toHaveValue('reset-password-123');
    expect(
      screen.getByRole('button', { name: 'Save new password' }),
    ).toBeEnabled();
  });
  it('restores Swedish and a dark theme on the reset link', () => {
    localStorage.setItem('sprintflow-language', 'SV');
    localStorage.setItem('sprintflow-theme', 'dark');
    renderAuth();
    expect(
      screen.getByRole('heading', { name: 'Välj ett nytt lösenord.' }),
    ).toBeInTheDocument();
    expect(document.querySelector('.auth-page')).toHaveAttribute(
      'data-theme',
      'dark',
    );
    expect(document.documentElement.lang).toBe('sv');
  });
});
