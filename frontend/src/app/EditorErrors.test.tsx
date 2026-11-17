import { fireEvent, screen, waitFor } from '@testing-library/react';
// Feature components need the query client and language providers.
import { renderWithProviders as render } from '../test/render';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '../api/client';
import { ActivityTimeline } from '../features/insights/ActivityTimeline';
import { BacklogManager } from '../features/planning/BacklogView';
import { SprintManager } from '../features/planning/SprintView';
import { TasksView } from '../features/tasks/TasksView';

vi.mock('../api/client', () => ({ api: vi.fn() }));
describe('Failed editor requests', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api).mockImplementation(async (_path, request) => {
      if (request?.method)
        throw new Error('You do not have permission to save this change.');
      return [];
    });
  });
  it.each(['story', 'sprint', 'task', 'update'])(
    'keeps the %s draft and displays its error inside the panel',
    async (kind) => {
      const onChanged = vi.fn();
      if (kind === 'story')
        render(
          <BacklogManager projectId={1} items={[]} onChanged={onChanged} />,
        );
      if (kind === 'sprint')
        render(
          <SprintManager projectId={1} items={[]} onChanged={onChanged} />,
        );
      if (kind === 'task') render(<TasksView projectId={1} hasProjects />);
      if (kind === 'update')
        render(
          <ActivityTimeline
            projectId={1}
            canManage
            items={[
              {
                id: 1,
                summary: 'Original event',
                actorEmail: 'qa@example.com',
                createdAt: '2026-10-11T12:00:00Z',
              },
            ]}
            onChanged={onChanged}
          />,
        );
      fireEvent.click(
        await screen.findByRole('button', {
          name: kind === 'update' ? 'Edit' : new RegExp('New ' + kind, 'i'),
        }),
      );
      const label =
        kind === 'sprint'
          ? 'Name'
          : kind === 'update'
            ? 'Update summary'
            : 'Title';
      fireEvent.change(screen.getByLabelText(label), {
        target: { value: 'Keep this draft' },
      });
      if (kind === 'story')
        fireEvent.change(screen.getByLabelText('Acceptance criteria'), {
          target: { value: 'Verify the outcome' },
        });
      if (kind === 'sprint') {
        fireEvent.change(screen.getByLabelText('Start date'), {
          target: { value: '2026-10-11' },
        });
        fireEvent.change(screen.getByLabelText('End date'), {
          target: { value: '2026-10-25' },
        });
      }
      fireEvent.click(
        screen.getByRole('button', {
          name: kind === 'update' ? 'Save' : 'Create ' + kind,
        }),
      );
      const error = await screen.findByRole('alert');
      expect(error).toHaveTextContent('permission');
      expect(error.closest('[role="dialog"]')).toBeInTheDocument();
      expect(screen.getByLabelText(label)).toHaveValue('Keep this draft');
      expect(onChanged).not.toHaveBeenCalled();
    },
  );
});

it('closes an unchanged task without a false dirty warning when the local date differs from UTC', async () => {
  const date = vi
    .spyOn(Date.prototype, 'toLocaleDateString')
    .mockReturnValue('2026-10-24');
  const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
  vi.mocked(api).mockImplementation(async (path) =>
    path === '/tasks?projectId=1'
      ? [
          {
            id: 1,
            version: 0,
            title: 'Timezone task',
            projectId: 1,
            status: 'To Do',
            storyPoints: 0,
            labels: [],
            checklist: [],
            dueAt: '2026-10-25T04:59:59Z',
          },
        ]
      : path.endsWith('/role')
        ? { role: 'Owner' }
        : [],
  );
  try {
    render(<TasksView projectId={1} hasProjects />);
    fireEvent.click(await screen.findByRole('button', { name: 'Edit' }));
    expect(screen.getByLabelText('Due date')).toHaveValue('2026-10-24');
    fireEvent.click(screen.getByRole('button', { name: 'Close panel' }));
    await waitFor(() =>
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument(),
    );
    expect(confirm).not.toHaveBeenCalled();
  } finally {
    date.mockRestore();
    confirm.mockRestore();
  }
});

it('exports exactly the current board filters including unscheduled and unassigned work', async () => {
  vi.mocked(api).mockImplementation(async (path) =>
    path === '/tasks?projectId=1'
      ? [
          {
            id: 1,
            title: 'Filtered',
            projectId: 1,
            status: 'To Do',
            storyPoints: 3,
            labels: ['Bug'],
            checklist: [],
          },
        ]
      : [],
  );
  render(<TasksView projectId={1} hasProjects />);
  await screen.findByRole('option', { name: 'Bug' });
  fireEvent.change(screen.getByRole('searchbox', { name: 'Search tasks' }), {
    target: { value: 'a & b' },
  });
  for (const [name, value] of [
    ['Filter status', 'To Do'],
    ['Filter sprint', 'unassigned'],
    ['Filter assignee', 'unassigned'],
    ['Filter due date', 'overdue'],
    ['Filter label', 'Bug'],
  ] as const)
    fireEvent.change(screen.getByRole('combobox', { name }), {
      target: { value },
    });
  fireEvent.click(screen.getByRole('checkbox', { name: 'Manually blocked' }));
  const url = new URL(
    screen
      .getByRole('link', { name: 'Export filtered tasks (CSV)' })
      .getAttribute('href')!,
    'http://localhost',
  );
  expect(Object.fromEntries(url.searchParams)).toEqual({
    status: 'To Do',
    sprint: 'backlog',
    assignee: 'unassigned',
    due: 'overdue',
    label: 'Bug',
    q: 'a & b',
    blocked: 'true',
  });
  expect(
    screen.getByRole('link', { name: 'Export filtered tasks (PDF)' }),
  ).toHaveAttribute('href', url.pathname.replace('.csv', '.pdf') + url.search);
});

it('waits for a status update before blocking with the returned task version', async () => {
  const task = {
    id: 12,
    projectId: 1,
    version: 0,
    title: 'Rapid changes',
    status: 'To Do',
    storyPoints: 3,
    labels: [],
    checklist: [],
  };
  let finish!: (value: unknown) => void;
  const pending = new Promise((resolve) => {
    finish = resolve;
  });
  vi.mocked(api).mockImplementation(async (path, request) => {
    if (request?.method && path === '/tasks/12') return pending;
    if (request?.method)
      return { ...task, version: 2, status: 'In Progress', blocked: true };
    return path === '/tasks?projectId=1' ? [task] : [];
  });
  render(<TasksView projectId={1} hasProjects />);
  const status = await screen.findByRole('combobox', { name: 'Move to' });
  fireEvent.change(status, { target: { value: 'In Progress' } });
  expect(screen.getByRole('button', { name: 'Block' })).toBeDisabled();
  expect(screen.getByRole('button', { name: 'Edit' })).toBeDisabled();
  finish({ ...task, version: 1, status: 'In Progress' });
  await waitFor(() =>
    expect(screen.getByRole('button', { name: 'Block' })).toBeEnabled(),
  );
  fireEvent.click(screen.getByRole('button', { name: 'Block' }));
  fireEvent.change(screen.getByLabelText('Block reason'), {
    target: { value: 'Waiting' },
  });
  fireEvent.click(screen.getByRole('button', { name: 'Block task' }));
  await waitFor(() =>
    expect(api).toHaveBeenCalledWith('/tasks/12/block?reason=Waiting', {
      method: 'PUT',
      headers: { 'If-Match': '"1"' },
    }),
  );
});
