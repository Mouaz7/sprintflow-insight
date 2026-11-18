import { screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Story, Task } from '../api/contracts';
import { BacklogManager } from '../features/planning/BacklogView';
import { TasksView } from '../features/tasks/TasksView';
import { mockApi } from '../test/api';
import { renderWithProviders } from '../test/render';

const statuses: Task['status'][] = ['To Do', 'In Progress', 'Review', 'Done'];
const tasks: Task[] = Array.from({ length: 5000 }, (_, index) => ({
  id: index + 1,
  title: `Task ${index + 1}`,
  status: statuses[index % 4]!,
  projectId: 1,
  storyPoints: 1,
}));
const stories: Story[] = Array.from({ length: 5000 }, (_, index) => ({
  id: index + 1,
  projectId: 1,
  title: `Story ${index + 1}`,
  acceptanceCriteria: 'Done when verified',
  storyPoints: 2,
  priorityId: 2,
  backlogPosition: index,
}));

// jsdom has no layout: give scroll regions a viewport and items a height,
// as a browser would after measuring them.
function fakeLayout() {
  vi.spyOn(HTMLElement.prototype, 'clientHeight', 'get').mockImplementation(
    function (this: HTMLElement) {
      return this.classList.contains('virtual-scroll') ? 900 : 0;
    },
  );
  vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockImplementation(
    function (this: HTMLElement) {
      return this.classList.contains('virtual-scroll') ? 400 : 0;
    },
  );
  vi.spyOn(HTMLElement.prototype, 'offsetHeight', 'get').mockImplementation(
    function (this: HTMLElement) {
      return this.dataset.index === undefined ? 0 : 300;
    },
  );
}

describe('Virtualized board and backlog (5,000 items)', () => {
  beforeEach(fakeLayout);
  afterEach(() => vi.restoreAllMocks());

  it('renders a 5,000-task board with a bounded DOM and bounded requests', async () => {
    const attachmentReads: string[] = [];
    mockApi((path) => {
      if (path === '/api/tasks?projectId=1') return tasks;
      if (path === '/api/projects/1/role') return { role: 'Owner' };
      if (path === '/api/auth/preferences') return { savedFilters: '[]' };
      if (/^\/api\/tasks\/\d+\/attachments$/.test(path))
        attachmentReads.push(path);
      return [];
    });
    const { container } = renderWithProviders(
      <TasksView hasProjects projectId={1} userEmail="qa@example.com" />,
    );
    expect(await screen.findByText('Task 1')).toBeInTheDocument();
    const columns = screen.getAllByRole('group', { name: /.+/ });
    expect(
      columns.filter((c) => c.className === 'virtual-scroll'),
    ).toHaveLength(4);
    const cards = container.querySelectorAll('.task-board article');
    expect(cards.length).toBeGreaterThan(4);
    expect(cards.length).toBeLessThanOrEqual(4 * 12);
    expect(screen.queryByText('Task 4999')).not.toBeInTheDocument();
    // Every column header still counts all of its 1,250 tasks.
    expect(screen.getAllByText('1250')).toHaveLength(4);
    expect(container.querySelectorAll('*').length).toBeLessThan(3000);
    await waitFor(() => expect(attachmentReads.length).toBeGreaterThan(0));
    expect(attachmentReads.length).toBeLessThanOrEqual(cards.length);
  });

  it('renders a 5,000-story backlog as a bounded window of table rows', async () => {
    mockApi(() => []);
    const { container } = renderWithProviders(
      <BacklogManager projectId={1} items={stories} onChanged={vi.fn()} />,
    );
    expect(
      await screen.findByRole('heading', { name: 'Story 1' }),
    ).toBeInTheDocument();
    const table = screen.getByRole('table');
    expect(table).toHaveAttribute('aria-rowcount', '5001');
    const rows = container.querySelectorAll('tbody tr:not(.virtual-spacer)');
    expect(rows.length).toBeGreaterThan(0);
    expect(rows.length).toBeLessThanOrEqual(20);
    expect(rows[0]).toHaveAttribute('aria-rowindex', '2');
    expect(container.querySelectorAll('*').length).toBeLessThan(1500);
  });

  it('keeps short lists unvirtualized', async () => {
    mockApi(() => []);
    renderWithProviders(
      <BacklogManager
        projectId={1}
        items={stories.slice(0, 10)}
        onChanged={vi.fn()}
      />,
    );
    await screen.findByRole('heading', { name: 'Story 10' });
    expect(screen.getByRole('table')).not.toHaveAttribute('aria-rowcount');
  });
});
