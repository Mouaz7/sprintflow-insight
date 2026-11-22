import {
  screen,
  render,
  waitFor,
  within,
  fireEvent,
} from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import { apiError, mockApi } from '../test/api';
import { App, legacyRedirect } from './App';
import { mockSignedIn } from './appTestFixtures';

function visit(path: string) {
  window.history.replaceState(null, '', path);
  return render(<App />);
}

describe('App.routing', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  it('maps the pre-A6 query links to their routes', () => {
    expect(legacyRedirect('?report=abc%2F1')).toBe('/report/abc%2F1');
    expect(legacyRedirect('?resetToken=t-1')).toBe('/reset-password?token=t-1');
    expect(legacyRedirect('?view=tasks')).toBeUndefined();
  });

  it('redirects an old shared report link and opens it without a session', async () => {
    const requests: string[] = [];
    mockApi((path) => {
      requests.push(path);
      return path === '/api/shared-reports/old-token'
        ? {
            projectName: 'Shared alpha',
            generatedAt: '2026-11-01T10:00:00Z',
            statusCounts: {},
          }
        : apiError(401, 'Sign in to continue.');
    });
    visit('/?report=old-token');
    expect(
      await screen.findByRole('heading', { name: 'Shared alpha' }),
    ).toBeInTheDocument();
    expect(window.location.pathname).toBe('/report/old-token');
    expect(requests).not.toContain('/api/auth/me');
  });

  it('redirects an old password reset link to the reset route', async () => {
    mockApi(() => apiError(401, 'Sign in to continue.'));
    visit('/?resetToken=reset-1');
    expect(
      await screen.findByRole('heading', { name: 'Choose a new password.' }),
    ).toBeInTheDocument();
    expect(window.location.pathname).toBe('/reset-password');
    expect(window.location.search).toBe('?token=reset-1');
  });

  it('opens a deep link to a view and marks it as the current page', async () => {
    mockSignedIn();
    visit('/TaSkS/');
    expect(
      await screen.findByRole('heading', { name: 'Tasks', level: 1 }),
    ).toBeInTheDocument();
    const navigation = screen.getByRole('navigation', {
      name: 'Main navigation',
    });
    expect(
      within(navigation).getByRole('link', { name: 'Tasks' }),
    ).toHaveAttribute('aria-current', 'page');
    expect(document.title).toBe('Tasks · SprintFlow');
  });

  it('sends unknown paths to the overview', async () => {
    mockSignedIn();
    visit('/no-such-page');
    await waitFor(() => expect(window.location.pathname).toBe('/overview'));
  });

  it('shows the sign-in page for a deep link without a session and keeps the address', async () => {
    mockApi(() => apiError(401, 'Sign in to continue.'));
    visit('/metrics');
    expect(
      await screen.findByRole('heading', { name: 'Welcome back.' }),
    ).toBeInTheDocument();
    expect(window.location.pathname).toBe('/metrics');
  });

  it('does not fetch protected project data before a session is known', async () => {
    const requests: string[] = [];
    mockApi((path) => {
      requests.push(path);
      return apiError(401, 'Sign in to continue.');
    });
    visit('/tasks');
    await screen.findByRole('heading', { name: 'Welcome back.' });
    expect(requests).toContain('/api/auth/me');
    expect(requests).not.toContain('/api/projects');
  });

  it('moves focus to the new page heading after navigation, but not on load', async () => {
    mockSignedIn();
    visit('/overview');
    const navigation = await screen.findByRole('navigation', {
      name: 'Main navigation',
    });
    await screen.findByRole('heading', { level: 1 });
    expect(document.activeElement).toBe(document.body);
    fireEvent.click(within(navigation).getByRole('link', { name: 'Projects' }));
    const heading = await screen.findByRole('heading', {
      name: 'Projects',
      level: 1,
    });
    await waitFor(() => expect(heading).toHaveFocus());
    expect(document.title).toBe('Projects · SprintFlow');
    fireEvent.change(screen.getByRole('combobox', { name: 'Language' }), {
      target: { value: 'SV' },
    });
    await waitFor(() => expect(document.title).toBe('Projekt · SprintFlow'));
  });

  it('has one main landmark and a skip link to it', async () => {
    mockSignedIn();
    visit('/overview');
    await screen.findByRole('navigation', { name: 'Main navigation' });
    expect(screen.getAllByRole('main')).toHaveLength(1);
    const skip = screen.getByRole('link', { name: 'Skip to content' });
    expect(skip).toHaveAttribute('href', '#workspace-page');
    expect(screen.getByRole('main')).toHaveAttribute('id', 'workspace-page');
  });
});
