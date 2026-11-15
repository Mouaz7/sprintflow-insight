import { render, screen } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import { api } from '../../api/client';
import { TasksView } from './TasksView';

vi.mock('../../api/client', () => ({ api: vi.fn() }));
beforeEach(() => {
  vi.mocked(api).mockReset();
});

const task = (title: string) => ({
  id: 5,
  title,
  status: 'To Do',
  projectId: 2,
  storyPoints: 1,
  labels: [],
  checklist: [],
  version: 0,
});

it('reloads the board quietly when the realtime signal changes', async () => {
  let title = 'Before';
  vi.mocked(api).mockImplementation(async (path) => {
    if (path === '/tasks?projectId=2') return [task(title)];
    if (path === '/projects/2/role') return { role: 'Owner' };
    return [];
  });
  const view = (signal: number) => (
    <TasksView hasProjects projectId={2} language="EN" refreshSignal={signal} />
  );
  const { rerender } = render(view(0));
  expect(await screen.findByText('Before')).toBeInTheDocument();

  title = 'Changed elsewhere';
  rerender(view(1));
  expect(await screen.findByText('Changed elsewhere')).toBeInTheDocument();
  expect(
    vi.mocked(api).mock.calls.filter(([path]) => path === '/projects/2/role'),
  ).toHaveLength(1);
});
