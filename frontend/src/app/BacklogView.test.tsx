import { fireEvent, render, screen } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { api } from '../api/client';
import { BacklogManager } from './BacklogView';

vi.mock('../api/client', () => ({ api: vi.fn() }));

it('shows the selected sprint backlog separately from unplanned stories', async () => {
  vi.mocked(api).mockResolvedValue([
    { id: 10, name: 'Sprint Alpha', projectId: 1 },
  ]);
  const base = {
    projectId: 1,
    acceptanceCriteria: 'Reviewed',
    storyPoints: 3,
    priorityId: 2,
  };
  render(
    <BacklogManager
      projectId={1}
      language="EN"
      onChanged={vi.fn()}
      items={[
        { ...base, id: 1, title: 'Planned story', sprintId: 10 },
        { ...base, id: 2, title: 'Future story' },
      ]}
    />,
  );
  await screen.findByRole('option', { name: 'Sprint Alpha' });
  const picker = screen.getByRole('combobox', {
    name: 'Filter backlog by sprint',
  });
  fireEvent.change(picker, { target: { value: '10' } });
  expect(
    screen.getByRole('heading', { name: 'Planned story' }),
  ).toBeInTheDocument();
  expect(
    screen.queryByRole('heading', { name: 'Future story' }),
  ).not.toBeInTheDocument();
  fireEvent.change(picker, { target: { value: 'backlog' } });
  expect(
    screen.getByRole('heading', { name: 'Future story' }),
  ).toBeInTheDocument();
  expect(
    screen.queryByRole('heading', { name: 'Planned story' }),
  ).not.toBeInTheDocument();
  expect(api).toHaveBeenCalledWith('/sprints?projectId=1');
});

it('sends the complete versioned manual order and keeps an error visible when ordering fails', async () => {
  const items = [
    {
      id: 1,
      version: 3,
      backlogPosition: 0,
      title: 'First',
      projectId: 1,
      acceptanceCriteria: 'Ready',
      storyPoints: 3,
      priorityId: 2,
    },
    {
      id: 2,
      version: 7,
      backlogPosition: 1,
      title: 'Second',
      projectId: 1,
      acceptanceCriteria: 'Ready',
      storyPoints: 3,
      priorityId: 2,
    },
  ];
  const changed = vi.fn();
  vi.mocked(api).mockImplementation(async (_path, request) => {
    if (request?.method)
      throw new Error(
        'The backlog changed. Reload it before changing the order.',
      );
    return [];
  });
  render(
    <BacklogManager
      projectId={1}
      language="EN"
      items={items}
      onChanged={changed}
    />,
  );
  fireEvent.click(screen.getByRole('button', { name: 'Move up: Second' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('backlog changed');
  expect(api).toHaveBeenCalledWith('/stories/project/1/order', {
    method: 'PUT',
    body: JSON.stringify({
      items: [
        { id: 2, version: 7 },
        { id: 1, version: 3 },
      ],
    }),
  });
  expect(changed).not.toHaveBeenCalled();
  expect(screen.getByRole('button', { name: 'Move up: Second' })).toBeEnabled();
});
