import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import { mockApi } from '../test/api';
import { renderWorkspace } from './testWorkspace';

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
    mockApi((fullPath) => {
      const path = fullPath.replace(/^\/api/, '');
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
      if (path === '/tasks/by-key/SF-13')
        return { id: 14, projectId: 2, key: 'SF-13' };
      if (path === '/tasks?projectId=2')
        return [task(13, 12, 'Numbered task'), task(14, 13, 'Other task')];
      return [];
    });
  });

  it('opens a hit in its project with the board narrowed to the task, and g o returns to the overview', async () => {
    renderWorkspace({ session: { user, projects }, path: '/overview' });
    fireEvent.keyDown(document.body, { key: 'k', ctrlKey: true });
    fireEvent.change(
      await screen.findByRole('combobox', { name: 'Search or run a command' }),
      {
        target: { value: 'staging' },
      },
    );
    const option = await screen.findByRole('option', { name: /Numbered task/ });
    fireEvent.keyDown(
      await screen.findByRole('combobox', { name: 'Search or run a command' }),
      { key: 'Enter' },
    );
    expect(option).not.toBeInTheDocument();

    await screen.findByRole('heading', { name: 'Tasks', level: 1 });
    expect(await screen.findByText('Numbered task')).toBeInTheDocument();
    expect(screen.queryByText('Other task')).not.toBeInTheDocument();
    expect(screen.getByRole('searchbox', { name: /Search tasks/ })).toHaveValue(
      'SF-12',
    );
    expect(screen.getByRole('combobox', { name: 'Project' })).toHaveValue('2');
    expect(
      within(
        screen.getByRole('navigation', { name: 'Main navigation' }),
      ).getByRole('link', { name: 'Tasks' }),
    ).toHaveAttribute('aria-current', 'page');

    // Retargeting another task on the same route must replace the initial filter.
    fireEvent.keyDown(document.body, { key: 'k', ctrlKey: true });
    const keyInput = screen.getByRole('combobox', {
      name: 'Search or run a command',
    });
    fireEvent.change(keyInput, { target: { value: 'SF-13' } });
    fireEvent.keyDown(keyInput, { key: 'Enter' });
    expect(await screen.findByText('Other task')).toBeInTheDocument();
    expect(screen.queryByText('Numbered task')).not.toBeInTheDocument();
    fireEvent.keyDown(document.body, { key: 'g' });
    fireEvent.keyDown(document.body, { key: 'o' });
    await waitFor(() =>
      expect(
        within(
          screen.getByRole('navigation', { name: 'Main navigation' }),
        ).getByRole('link', { name: 'Overview' }),
      ).toHaveAttribute('aria-current', 'page'),
    );
  });

  it('opens a new-task editor from a focused board without losing the request', async () => {
    localStorage.setItem('sprintflow-active-project', '2');
    renderWorkspace({ session: { user, projects }, path: '/tasks?task=SF-13' });
    await screen.findByRole('heading', { name: 'Tasks', level: 1 });
    expect(await screen.findByText('Other task')).toBeInTheDocument();
    fireEvent.keyDown(document.body, { key: 'c' });
    const editor = await screen.findByRole('dialog', { name: 'New task' });
    expect(editor).toBeVisible();
    fireEvent.keyDown(editor, { key: 'Escape' });
    await waitFor(() =>
      expect(
        screen.getByRole('searchbox', { name: /Search tasks/ }),
      ).toHaveValue(''),
    );
  });
});
