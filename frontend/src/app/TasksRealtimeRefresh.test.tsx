import { act, screen } from '@testing-library/react';
import { expect, it } from 'vitest';
import { queryKeys } from '../api/queries';
import { renderWorkspace } from './testWorkspace';
import { mockApi } from '../test/api';

it('refreshes board data from the shared cache without reloading roles', async () => {
  let title = 'Before';
  let roleReads = 0;
  mockApi((path) => {
    if (path === '/api/tasks?projectId=2')
      return [
        {
          id: 5,
          title,
          status: 'To Do',
          projectId: 2,
          storyPoints: 1,
          labels: [],
          checklist: [],
          version: 0,
        },
      ];
    if (path === '/api/projects/2/role') {
      roleReads++;
      return { role: 'Owner' };
    }
    if (path === '/api/auth/preferences')
      return { onboardingCompleted: true, savedFilters: '[]' };
    return [];
  });
  const { client } = renderWorkspace({
    path: '/tasks',
    session: {
      user: { id: 1, email: 'qa@example.com' },
      projects: [
        { id: 2, name: 'Project', key: 'SF', ownerEmail: 'qa@example.com' },
      ],
    },
  });
  expect(await screen.findByText('Before')).toBeInTheDocument();
  title = 'Changed elsewhere';
  await act(async () => {
    await client.invalidateQueries({ queryKey: queryKeys.tasks(2) });
  });
  expect(await screen.findByText('Changed elsewhere')).toBeInTheDocument();
  expect(roleReads).toBe(1);
});
