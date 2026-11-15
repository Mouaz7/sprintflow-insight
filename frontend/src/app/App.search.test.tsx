import { fireEvent, render, screen, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '../api/client';
import { Workspace } from './Workspace';

vi.mock('../api/client', () => ({ api: vi.fn() }));

const user = { id: 1, email: 'qa@example.com' };
const projects = [
  { id: 1, name: 'Alpha', key: 'AL', ownerEmail: user.email },
  { id: 2, name: 'Beta', key: 'SF', ownerEmail: user.email },
];
const task = (id: number, number: number, title: string) => ({
  id,
  projectId: 2,
  number,
  projectKey: 'SF',
  key: `SF-${number}`,
  title,
  status: 'To Do',
  storyPoints: 1,
  version: 0,
});

describe('Search from the workspace', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    vi.mocked(api).mockReset();
    vi.mocked(api).mockImplementation(async (path) => {
      if (path.endsWith('/role')) return { role: 'Owner' };
      if (path.endsWith('/metrics'))
        return { taskCount: 0, sprintHealth: { status: 'Healthy' } };
      if (path === '/auth/preferences')
        return { savedFilters: '[]', onboardingCompleted: true };
      if (path.startsWith('/search?'))
        return {
          items: [
            {
              type: 'comment',
              id: 90,
              projectId: 2,
              projectKey: 'SF',
              projectName: 'Beta',
              taskId: 13,
              key: 'SF-12',
              title: 'Numbered task',
              titleSegments: [{ text: 'Numbered task', match: false }],
              snippet: [{ text: 'staging', match: true }],
              exactMatch: false,
            },
          ],
          nextCursor: null,
        };
      if (path === '/tasks?projectId=2')
        return [task(13, 12, 'Numbered task'), task(14, 13, 'Other task')];
      return [];
    });
  });

  it('opens a hit in its project with the board narrowed to the task, and g o returns to the overview', async () => {
    render(
      <Workspace
        session={{ user, projects }}
        onProjectsChanged={vi.fn()}
        onLogout={vi.fn()}
        onUserChanged={vi.fn()}
        onAccountDeleted={vi.fn()}
      />,
    );
    fireEvent.keyDown(document.body, { key: 'k', ctrlKey: true });
    fireEvent.change(
      screen.getByRole('combobox', { name: 'Search or run a command' }),
      {
        target: { value: 'staging' },
      },
    );
    const option = await screen.findByRole('option', { name: /Numbered task/ });
    fireEvent.keyDown(
      screen.getByRole('combobox', { name: 'Search or run a command' }),
      { key: 'Enter' },
    );
    expect(option).not.toBeInTheDocument();

    expect(await screen.findByText('Numbered task')).toBeInTheDocument();
    expect(screen.queryByText('Other task')).not.toBeInTheDocument();
    expect(screen.getByRole('searchbox', { name: /Search tasks/ })).toHaveValue(
      'SF-12',
    );
    expect(screen.getByRole('combobox', { name: 'Project' })).toHaveValue('2');
    expect(
      within(
        screen.getByRole('navigation', { name: 'Main navigation' }),
      ).getByRole('button', { name: 'Tasks' }),
    ).toHaveAttribute('aria-current', 'page');

    fireEvent.keyDown(document.body, { key: 'g' });
    fireEvent.keyDown(document.body, { key: 'o' });
    expect(
      within(
        screen.getByRole('navigation', { name: 'Main navigation' }),
      ).getByRole('button', { name: 'Overview' }),
    ).toHaveAttribute('aria-current', 'page');
  });
});
