import { fireEvent, render, screen, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from './App';
import { mockSignedIn } from './appTestFixtures';
describe('App.navigation', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    vi.unstubAllGlobals();
  });
  it('keeps the current view and selected project when the workspace is reloaded', async () => {
    mockSignedIn();
    const first = render(<App />);
    const navigation = await screen.findByRole('navigation', {
      name: 'Main navigation',
    });
    fireEvent.change(screen.getByRole('combobox', { name: 'Project' }), {
      target: { value: '2' },
    });
    fireEvent.click(within(navigation).getByRole('link', { name: 'Tasks' }));
    await screen.findByRole('heading', { name: 'Tasks' });
    first.unmount();

    render(<App />);
    await screen.findByRole('heading', { name: 'Tasks' });
    expect(screen.getByRole('combobox', { name: 'Project' })).toHaveValue('2');
    expect(
      within(
        screen.getByRole('navigation', { name: 'Main navigation' }),
      ).getByRole('link', { name: 'Tasks' }),
    ).toHaveAttribute('aria-current', 'page');
  });
  it.each(['not-a-view', 'constructor'])(
    'ignores an invalid remembered view: %s',
    async (saved) => {
      sessionStorage.setItem('sprintflow-view-v1:1', saved);
      mockSignedIn();
      render(<App />);
      const navigation = await screen.findByRole('navigation', {
        name: 'Main navigation',
      });
      expect(
        within(navigation).getByRole('link', { name: 'Overview' }),
      ).toHaveAttribute('aria-current', 'page');
    },
  );
  it('does not restore the remembered view of another account', async () => {
    sessionStorage.setItem('sprintflow-view-v1:2', 'profile');
    mockSignedIn();
    render(<App />);
    const navigation = await screen.findByRole('navigation', {
      name: 'Main navigation',
    });
    expect(
      within(navigation).getByRole('link', { name: 'Overview' }),
    ).toHaveAttribute('aria-current', 'page');
  });
  it('marks the current workspace page and provides a profile link in mobile navigation', async () => {
    mockSignedIn();
    render(<App />);
    const navigation = await screen.findByRole('navigation', {
      name: 'Main navigation',
    });
    const overview = within(navigation).getByRole('link', {
      name: 'Overview',
    });
    expect(overview).toHaveAttribute('aria-current', 'page');
    const tasks = within(navigation).getByRole('link', { name: 'Tasks' });
    fireEvent.click(tasks);
    expect(tasks).toHaveAttribute('aria-current', 'page');
    expect(overview).not.toHaveAttribute('aria-current');
    const profile = within(navigation).getByRole('link', { name: 'Profile' });
    fireEvent.click(profile);
    expect(
      await screen.findByRole('heading', { name: 'Your profile' }),
    ).toBeInTheDocument();
    expect(profile).toHaveAttribute('aria-current', 'page');
    expect(tasks).not.toHaveAttribute('aria-current');
  });
});
