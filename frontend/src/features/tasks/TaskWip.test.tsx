import { screen } from '@testing-library/react';
import { expect, it } from 'vitest';
import { mockApi } from '../../test/api';
import { renderWithProviders } from '../../test/render';
import { TasksView } from './TasksView';

it('compares WIP with all column tasks even when the board is filtered to one card', async () => {
  mockApi((path) => {
    if (path.endsWith('/role')) return { role: 'Owner' };
    if (path.startsWith('/api/tasks?'))
      return [1, 2].map((id) => ({
        id,
        projectId: 1,
        key: `QA-${id}`,
        title: `Task ${id}`,
        status: 'In Progress',
        storyPoints: 1,
        version: 0,
      }));
    if (path.endsWith('/dependencies'))
      return { dependencies: [], blockedTaskIds: [], externalBlocks: [] };
    if (path.endsWith('/wip-limits'))
      return { version: 1, limits: [{ status: 'In Progress', limit: 1 }] };
    return [];
  });
  renderWithProviders(
    <TasksView hasProjects projectId={1} focusTaskKey="QA-1" />,
    { language: 'EN' },
  );
  expect(
    await screen.findByText('WIP: 2 / 1 · Over the WIP limit'),
  ).toBeVisible();
  expect(screen.getByText('Task 1')).toBeVisible();
  expect(screen.queryByText('Task 2')).not.toBeInTheDocument();
});
