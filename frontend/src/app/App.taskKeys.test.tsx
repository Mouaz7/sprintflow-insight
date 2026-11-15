import { fireEvent, render, screen, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '../api/client';
import { Workspace } from './Workspace';

vi.mock('../api/client', () => ({ api: vi.fn() }));

const user = { id: 1, email: 'qa@example.com' };

describe('Task identifiers', () => {
  const projects = [
    { id: 1, name: 'Alpha', key: 'SF', ownerEmail: user.email },
  ];
  beforeEach(() => {
    localStorage.clear();
    vi.mocked(api).mockReset();
    vi.mocked(api).mockImplementation(async (path) => {
      if (path.endsWith('/role')) return { role: 'Owner' };
      if (path.endsWith('/metrics'))
        return { taskCount: 0, sprintHealth: { status: 'Healthy' } };
      if (path === '/auth/preferences')
        return { savedFilters: '[]', onboardingCompleted: true };
      if (path.startsWith('/tasks?'))
        return [
          {
            id: 13,
            projectId: 1,
            number: 12,
            projectKey: 'SF',
            key: 'SF-12',
            title: 'Numbered task',
            status: 'To Do',
            storyPoints: 1,
            version: 0,
          },
        ];
      return [];
    });
  });

  function openTasks() {
    render(
      <Workspace
        session={{ user, projects }}
        onProjectsChanged={vi.fn()}
        onLogout={vi.fn()}
        onUserChanged={vi.fn()}
        onAccountDeleted={vi.fn()}
      />,
    );
    fireEvent.click(
      within(
        screen.getByRole('navigation', { name: 'Main navigation' }),
      ).getByRole('button', {
        name: 'Tasks',
      }),
    );
  }

  it('shows SF-12 on the card, finds it by key and names the editor after it', async () => {
    openTasks();
    expect(await screen.findByText('SF-12')).toHaveClass('task-key');
    fireEvent.change(screen.getByRole('searchbox', { name: /Search tasks/ }), {
      target: { value: 'sf-12' },
    });
    expect(screen.getByText('Numbered task')).toBeInTheDocument();
    const [edit] = await screen.findAllByRole('button', { name: 'Edit' });
    fireEvent.click(edit!);
    expect(
      screen.getByRole('dialog', { name: 'Edit task · SF-12' }),
    ).toBeInTheDocument();
  });
});
