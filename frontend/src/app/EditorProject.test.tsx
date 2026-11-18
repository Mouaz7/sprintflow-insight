import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import { api } from '../api/client';
import { renderWorkspace } from './testWorkspace';

vi.mock('../api/client', () => ({ api: vi.fn() }));
const projects = [
  { id: 1, name: 'Alpha', ownerEmail: 'qa@example.com' },
  { id: 2, name: 'Beta', ownerEmail: 'qa@example.com' },
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
    if (path.startsWith('/stories/project/'))
      return [
        {
          id: 11,
          projectId: Number(path.split('/').at(-1)),
          title: 'Existing story',
          acceptanceCriteria: 'Ready',
          storyPoints: 1,
          priorityId: 2,
        },
      ];
    if (path.startsWith('/sprints?'))
      return [
        {
          id: 12,
          projectId: Number(path.split('=').at(-1)),
          name: 'Existing sprint',
          status: 'Planned',
          totalStoryPoints: 0,
        },
      ];
    if (path.startsWith('/tasks?'))
      return [
        {
          id: 13,
          projectId: Number(path.split('=').at(-1)),
          title: 'Existing task',
          status: 'To Do',
          storyPoints: 0,
        },
      ];
    return [];
  });
});
function workspace() {
  renderWorkspace({
    session: { user: { id: 1, email: 'qa@example.com' }, projects },
  });
}

it.each([
  ['Backlog', 'New story'],
  ['Sprints', 'New sprint'],
  ['Tasks', 'New task'],
])(
  'shows the selected project in %s create and edit panels after switching projects',
  async (view, trigger) => {
    workspace();
    fireEvent.click(
      within(
        screen.getByRole('navigation', { name: 'Main navigation' }),
      ).getByRole('link', { name: view }),
    );
    const create = await screen.findByRole('button', {
      name: new RegExp(trigger),
    });
    await waitFor(() => expect(create).toBeEnabled());
    fireEvent.click(create);
    let dialog = screen.getByRole('dialog');
    expect(
      within(dialog).getByRole('combobox', { name: 'Project' }),
    ).toHaveValue('1');
    fireEvent.click(
      within(dialog).getByRole('button', { name: 'Close panel' }),
    );
    fireEvent.change(screen.getByRole('combobox', { name: 'Project' }), {
      target: { value: '2' },
    });
    const createInBeta = await screen.findByRole('button', {
      name: new RegExp(trigger),
    });
    await waitFor(() => expect(createInBeta).toBeEnabled());
    fireEvent.click(createInBeta);
    dialog = screen.getByRole('dialog');
    expect(
      within(dialog).getByRole('combobox', { name: 'Project' }),
    ).toHaveValue('2');
    fireEvent.click(
      within(dialog).getByRole('button', { name: 'Close panel' }),
    );
    fireEvent.click(
      (await screen.findAllByRole('button', { name: 'Edit' }))[0]!,
    );
    expect(
      screen.getByRole('dialog').querySelector('.editor-project'),
    ).toHaveTextContent('Project:Beta');
  },
);

it('does not label a new project as belonging to the previously selected project', async () => {
  workspace();
  fireEvent.click(
    within(
      screen.getByRole('navigation', { name: 'Main navigation' }),
    ).getByRole('link', { name: 'Projects' }),
  );
  fireEvent.click(
    await screen.findByRole('button', { name: /Create project/ }),
  );
  expect(
    screen.getByRole('dialog').querySelector('.editor-project'),
  ).not.toBeInTheDocument();
});
