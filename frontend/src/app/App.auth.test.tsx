import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from './App';
import { apiError, mockApi } from '../test/api';
describe('App.auth', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    vi.unstubAllGlobals();
  });
  it('shows the sign-in page when the server reports no active session', async () => {
    mockApi(() => apiError(401, 'Sign in to continue.'));
    render(<App />);
    expect(
      await screen.findByRole('heading', { name: 'Welcome back.' }),
    ).toBeInTheDocument();
  });
  it('restores and saves the theme on sign-in, registration and password recovery', async () => {
    localStorage.setItem('sprintflow-theme', 'dark');
    mockApi(() => apiError(401, 'Sign in to continue.'));
    render(<App />);
    await screen.findByRole('heading', { name: 'Welcome back.' });
    expect(document.querySelector('.auth-page')).toHaveAttribute(
      'data-theme',
      'dark',
    );
    fireEvent.click(
      screen.getByRole('button', { name: 'Switch to light mode' }),
    );
    expect(document.querySelector('.auth-page')).toHaveAttribute(
      'data-theme',
      'light',
    );
    expect(localStorage.getItem('sprintflow-theme')).toBe('light');
    fireEvent.click(screen.getByRole('button', { name: 'Create account' }));
    expect(
      screen.getByRole('heading', { name: 'Create your account.' }),
    ).toBeInTheDocument();
    expect(document.querySelector('.auth-page')).toHaveAttribute(
      'data-theme',
      'light',
    );
    fireEvent.click(screen.getByRole('button', { name: 'Log in' }));
    fireEvent.click(screen.getByRole('button', { name: 'Forgot password?' }));
    fireEvent.click(
      screen.getByRole('button', { name: 'Switch to dark mode' }),
    );
    expect(
      screen.getByRole('heading', { name: 'Reset your password.' }),
    ).toBeInTheDocument();
    expect(document.querySelector('.auth-page')).toHaveAttribute(
      'data-theme',
      'dark',
    );
    expect(localStorage.getItem('sprintflow-theme')).toBe('dark');
  });
  it('clearly explains the next steps when login credentials do not work', async () => {
    mockApi(async (input) => {
      const url = String(input);
      if (url.endsWith('/auth/me') || url.endsWith('/projects'))
        return apiError(401, 'Sign in to continue.');
      if (url.endsWith('/auth/csrf'))
        return { token: 'csrf', headerName: 'X-CSRF-TOKEN' };
      if (url.endsWith('/auth/login'))
        return apiError(401, 'Incorrect email or password.');
      return {};
    });
    render(<App />);
    fireEvent.change(await screen.findByLabelText('Email'), {
      target: { value: 'new@example.com' },
    });
    fireEvent.change(screen.getByLabelText('Password'), {
      target: { value: 'password1' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Log in' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'If you haven’t created an account yet, choose Create account.',
    );
    expect(screen.getByRole('alert')).toHaveTextContent(
      'check your email and password or reset your password',
    );
  });
  it('presents password recovery as a focused card and still sends the reset request', async () => {
    const requests: string[] = [];
    mockApi(async (input) => {
      const url = String(input);
      requests.push(url);
      if (url.endsWith('/auth/me') || url.endsWith('/projects'))
        return apiError(401, 'Sign in to continue.');
      if (url.endsWith('/auth/csrf'))
        return { token: 'csrf', headerName: 'X-CSRF-TOKEN' };
      if (url.endsWith('/auth/password/reset/request'))
        return {
          message:
            'If the address belongs to an account, a reset link will be sent.',
        };
      return {};
    });
    render(<App />);
    fireEvent.click(
      await screen.findByRole('button', { name: 'Forgot password?' }),
    );
    expect(
      await screen.findByRole('heading', { name: 'Reset your password.' }),
    ).toBeInTheDocument();
    expect(screen.getByText('ACCOUNT RECOVERY')).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: /Back to log in/ }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'Create account' }),
    ).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Email'), {
      target: { value: 'mouaz@example.com' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Send reset link' }));
    expect(
      await screen.findByText(
        'If the address belongs to an account, a reset link will be sent.',
      ),
    ).toBeInTheDocument();
    expect(requests).toContain('/api/auth/password/reset/request');
  });
});
