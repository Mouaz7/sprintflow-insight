import { fireEvent, render, screen, within } from '@testing-library/react';
import { beforeEach, describe, it } from 'vitest';
import { apiError, mockApi } from '../test/api';
import { expectNoSeriousA11yViolations } from '../test/axe';
import { App } from './App';
import { mockSignedIn } from './appTestFixtures';

function visit(path: string) {
  window.history.replaceState(null, '', path);
  return render(<App />);
}

describe('App.a11y (axe, WCAG 2.2 A/AA rules)', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  it.each(['EN', 'SV'])(
    'has no serious findings on the sign-in page (%s, dark)',
    async (language) => {
      localStorage.setItem('sprintflow-language', language);
      localStorage.setItem('sprintflow-theme', 'dark');
      mockApi(() => apiError(401, 'Sign in to continue.'));
      const { container } = visit('/');
      await screen.findByRole('heading', { level: 1 });
      await expectNoSeriousA11yViolations(container);
    },
  );

  it('has no serious findings on the password reset form', async () => {
    mockApi(() => apiError(401, 'Sign in to continue.'));
    const { container } = visit('/reset-password?token=t');
    await screen.findByRole('heading', { name: 'Choose a new password.' });
    await expectNoSeriousA11yViolations(container);
  });

  it.each(['/overview', '/tasks', '/backlog', '/projects', '/profile'])(
    'has no serious findings on %s',
    async (path) => {
      mockSignedIn();
      const { container } = visit(path);
      await screen.findByRole('heading', { level: 1 });
      await expectNoSeriousA11yViolations(container);
    },
  );

  it('has no serious findings with the mobile menu open', async () => {
    mockSignedIn();
    const { container } = visit('/overview');
    fireEvent.click(await screen.findByRole('button', { name: 'Open menu' }));
    within(screen.getByRole('dialog')).getByRole('navigation', {
      name: 'Mobile navigation',
    });
    await expectNoSeriousA11yViolations(container);
  });

  it('has no serious findings on a shared report', async () => {
    mockApi((path) =>
      path.startsWith('/api/shared-reports/')
        ? {
            projectName: 'Shared alpha',
            generatedAt: '2026-11-01T10:00:00Z',
            taskCount: 4,
            statusCounts: { Done: 2 },
          }
        : apiError(401, 'Sign in to continue.'),
    );
    const { container } = visit('/report/token');
    await screen.findByRole('heading', { name: 'Shared alpha' });
    await expectNoSeriousA11yViolations(container);
  });
});
