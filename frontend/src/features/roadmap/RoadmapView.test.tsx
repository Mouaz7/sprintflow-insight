import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { Epic, Project, RoadmapSnapshot } from '../../api/contracts';
import { mockApi } from '../../test/api';
import { renderWithProviders } from '../../test/render';
import { ConfirmProvider } from '../../shared/ui/ui';
import { RoadmapView } from './RoadmapView';

const project: Project = { id: 1, name: 'Launch', ownerEmail: 'owner@test.se' };
const epic: Epic = {
  id: 10,
  title: 'Customer portal',
  description: '<img src=x onerror=alert(1)>',
  color: '#4f46e5',
  startDate: '2026-11-17',
  targetDate: '2026-12-10',
  version: 2,
};
const task = (id: number) => ({
  id,
  key: `QA-${id}`,
  title: `Task ${id}`,
  status: 'To Do',
  sprintId: null,
});
const forecast = {
  start: '2026-11-17',
  expectedDays: 3,
  standardDeviationDays: 1,
  expectedFinish: '2026-11-19',
  probabilityOnTime: 0.95,
  estimatedTasks: 2,
  openTasks: 2,
  unplannedStories: 0,
  criticalPath: [task(1), task(2)],
};
const snapshot: RoadmapSnapshot = {
  today: '2026-11-17',
  epics: [
    {
      epic,
      plannedStart: epic.startDate,
      plannedEnd: epic.targetDate,
      progress: 0.5,
      totalPoints: 8,
      donePoints: 4,
      taskCount: 2,
      doneTaskCount: 1,
      storyCount: 2,
      storyIds: [1],
      sprintIds: [],
      forecast,
    },
  ],
  sprints: [],
  epicLinks: [],
  dependencies: {
    dependencies: [{ id: 1, blocker: task(1), blocked: task(2), open: true }],
    blockedTaskIds: [2],
    externalBlocks: [{ task: task(2), blocker: task(1) }],
  },
  project: forecast,
};

function mount({
  role = 'Owner',
  data = snapshot,
  archived = false,
  language = 'EN' as 'EN' | 'SV',
  write = (_path: string, _body: string | undefined) => HttpResponse.json({}),
} = {}) {
  const writes: { path: string; body: string | undefined }[] = [];
  mockApi((path, request) => {
    if (path === '/api/auth/csrf')
      return { token: 'test', headerName: 'X-CSRF-TOKEN' };
    if (request.method !== 'GET') {
      writes.push({ path, body: request.body });
      return write(path, request.body);
    }
    if (path.endsWith('/role')) return { role };
    if (path.endsWith('/roadmap')) return data;
    if (path.endsWith('/estimates')) return [];
    if (path.startsWith('/api/tasks?'))
      return Array.from({ length: 5000 }, (_, i) => task(i + 1));
    if (path.includes('/stories/project/'))
      return [{ id: 1, title: 'Welcome', version: 7, epicId: null }];
    throw new Error(`Unexpected read: ${path}`);
  });
  renderWithProviders(
    <ConfirmProvider>
      <RoadmapView
        project={{
          ...project,
          ...(archived ? { archivedAt: '2026-11-17T00:00:00Z' } : {}),
        }}
      />
    </ConfirmProvider>,
    { language, path: '/roadmap?epic=10' },
  );
  return writes;
}

describe('Roadmap', () => {
  it('connects selected epic, timeline, critical path and real text without rendering markup', async () => {
    mount();
    expect(
      await screen.findByRole('heading', { name: 'Customer portal' }),
    ).toBeVisible();
    expect(screen.getByLabelText('Focus on an epic')).toHaveValue('10');
    expect(screen.getByText(epic.description ?? '')).toBeVisible();
    expect(document.querySelector('img')).toBeNull();
    expect(screen.getByText('95%')).toBeVisible();
    expect(
      screen.getByRole('region', { name: 'Delivery timeline' }),
    ).toHaveAttribute('tabindex', '0');
    fireEvent.click(screen.getByText('Estimated critical path (2)'));
    expect(
      screen.getAllByRole('link', { name: 'QA-1 · Task 1' })[0],
    ).toHaveAttribute('href', '/tasks?task=QA-1');
    fireEvent.click(screen.getByText('Dates and progress as a table'));
    expect(screen.getByRole('table')).toBeVisible();
  });

  it.each([
    { role: 'Viewer', archived: false },
    { role: 'Owner', archived: true },
  ])(
    'shows a readable but unwritable roadmap for $role archived=$archived',
    async (options) => {
      mount(options);
      await screen.findByRole('heading', { name: 'Customer portal' });
      expect(
        screen.queryByRole('button', { name: 'New epic' }),
      ).not.toBeInTheDocument();
      expect(
        screen.queryByRole('button', { name: 'Delete epic' }),
      ).not.toBeInTheDocument();
      expect(screen.getByText(/You can explore this roadmap/)).toBeVisible();
    },
  );

  it('keeps incomplete forecasts explicit in Swedish', async () => {
    mount({
      language: 'SV',
      data: {
        ...snapshot,
        epics: [
          {
            ...snapshot.epics[0]!,
            forecast: {
              ...forecast,
              expectedFinish: null,
              probabilityOnTime: null,
              estimatedTasks: 1,
              unplannedStories: 1,
            },
          },
        ],
      },
    });
    expect(await screen.findByText(/1 av 2 återstående/)).toBeVisible();
    expect(screen.getByText(/1 story behöver/)).toBeVisible();
    expect(screen.getByText(/Dela upp stories/)).toBeVisible();
    expect(screen.queryByText('95 %')).not.toBeInTheDocument();
  });

  it('preserves epic drafts after a stale write and sends the captured revision', async () => {
    const writes = mount({
      write: () => HttpResponse.json({ detail: 'Stale' }, { status: 412 }),
    });
    await screen.findByRole('heading', { name: 'Customer portal' });
    fireEvent.click(screen.getByRole('button', { name: 'Edit' }));
    const dialog = screen.getByRole('dialog');
    fireEvent.change(within(dialog).getByLabelText('Epic title'), {
      target: { value: 'My draft' },
    });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Save' }));
    await within(dialog).findByText(/Someone else changed/);
    expect(within(dialog).getByLabelText('Epic title')).toHaveValue('My draft');
    expect(writes[0]?.path).toBe('/api/projects/1/epics/10');
    expect(JSON.parse(writes[0]?.body ?? '{}').title).toBe('My draft');
  });

  it('bounds 5000 task choices, filters them and displays the server cycle chain without losing choices', async () => {
    const user = userEvent.setup();
    const writes = mount({
      write: () =>
        HttpResponse.json(
          { cycleKeys: ['QA-1', 'QA-4999', 'QA-1'] },
          { status: 409 },
        ),
    });
    await screen.findByRole('heading', { name: 'Customer portal' });
    await user.click(screen.getByRole('button', { name: 'Add dependency' }));
    const dialog = screen.getByRole('dialog');
    const blocker = await within(dialog).findByRole('combobox', {
      name: 'Must finish first',
    });
    expect(within(blocker).getAllByRole('option').length).toBeLessThanOrEqual(
      51,
    );
    await user.selectOptions(blocker, '1');
    const fields = within(dialog).getAllByRole('group');
    const waitingField = fields.find((field) =>
      within(field).queryByRole('combobox', { name: 'Waiting task' }),
    );
    expect(waitingField).toBeDefined();
    if (!waitingField) throw new Error('Missing waiting-task field');
    await user.type(within(waitingField).getByRole('searchbox'), 'QA-4999');
    await user.selectOptions(
      within(dialog).getByRole('combobox', { name: 'Waiting task' }),
      '4999',
    );
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));
    expect(
      await within(dialog).findByText(/QA-1 → QA-4999 → QA-1/),
    ).toBeVisible();
    expect(blocker).toHaveValue('1');
    expect(JSON.parse(writes[0]?.body ?? '{}')).toEqual({
      blockerTaskId: 1,
      blockedTaskId: 4999,
    });
  });

  it('assigns a story while retaining its revision and does not silently move its sprint', async () => {
    const writes = mount();
    await screen.findByRole('heading', { name: 'Customer portal' });
    fireEvent.click(screen.getByRole('button', { name: 'Story epic' }));
    const dialog = screen.getByRole('dialog');
    fireEvent.change(
      await within(dialog).findByRole('combobox', { name: 'Choose a story' }),
      { target: { value: '1' } },
    );
    fireEvent.click(within(dialog).getByRole('button', { name: 'Save' }));
    await waitFor(() =>
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument(),
    );
    expect(writes[0]).toEqual({
      path: '/api/projects/1/stories/1/epic',
      body: '{"epicId":10}',
    });
  });
});
