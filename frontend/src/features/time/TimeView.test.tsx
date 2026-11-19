import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { describe, expect, it } from 'vitest';
import { mockApi } from '../../test/api';
import { server } from '../../test/server';
import { renderWithProviders } from '../../test/render';
import { expectNoSeriousA11yViolations } from '../../test/axe';
import { ConfirmProvider } from '../../shared/ui/ui';
import type { Capacity, TimeEntry } from './contracts';
import { TimeView } from './TimeView';
import { timeKey } from './queries';

const project = { id: 1, name: 'Time', ownerEmail: 'owner@test.se' };
const user = { id: 1, email: project.ownerEmail };
const task = {
  id: 3,
  key: 'TM-3',
  title: 'Accessible checkout',
  projectId: 1,
  sprintId: 10,
  status: 'To Do',
  storyPoints: 3,
  estimateMinutes: 120,
  version: 0,
};
const initial: TimeEntry = {
  id: 4,
  taskId: 3,
  taskKey: 'TM-3',
  taskTitle: task.title,
  userId: 1,
  userEmail: user.email,
  spentOn: '2026-11-18',
  minutes: 150,
  running: false,
  startedAt: null,
  note: 'Pairing',
  version: 2,
};
const capacity: Capacity = {
  sprintId: 10,
  sprintName: 'Delivery',
  startDate: '2026-11-16',
  endDate: '2026-11-27',
  workingDays: 10,
  members: [
    {
      userId: 1,
      email: user.email,
      role: 'Owner',
      configured: true,
      minutesPerDay: 60,
      availabilityPercent: 100,
      pointCapacity: 5,
      daysOff: [],
      version: 2,
      availableDays: 10,
      availableMinutes: 600,
      effectivePoints: 5,
      committedMinutes: 1200,
      committedPoints: 8,
      unestimatedTasks: 0,
      loggedMinutes: 150,
      loadPercent: 200,
      overbooked: true,
      pointsLoadPercent: 160,
      pointsOverbooked: true,
    },
  ],
  team: {
    availableMinutes: 600,
    effectivePoints: 5,
    committedMinutes: 1200,
    committedPoints: 8,
    unassignedMinutes: 0,
    unassignedPoints: 0,
    loggedMinutes: 150,
    loadPercent: 200,
    overbooked: true,
    pointsLoadPercent: 160,
    pointsOverbooked: true,
  },
  warnings: [
    { code: 'MEMBER_OVERBOOKED', userId: 1 },
    { code: 'TEAM_OVERBOOKED', userId: null },
  ],
};
function mount({
  role = 'Owner',
  archived = false,
  language = 'EN' as 'EN' | 'SV',
  paged = false,
  reject = false,
} = {}) {
  let rows = [structuredClone(initial)];
  let timer: TimeEntry | null = null;
  let failed = false;
  let rejectNext = reject;
  let revision = 0;
  const writes: {
    method: string;
    path: string;
    body: Record<string, unknown>;
  }[] = [];
  mockApi((path, request) => {
    if (path === '/api/auth/csrf')
      return { headerName: 'X-CSRF-TOKEN', token: 'test' };
    if (path.endsWith('/role')) return { role };
    if (path === '/api/sprints?projectId=1')
      return [{ id: 10, name: 'Delivery', projectId: 1 }];
    if (path === '/api/tasks?projectId=1')
      return [{ ...task, version: revision }];
    if (request.method !== 'GET') {
      const body = JSON.parse(request.body ?? '{}') as Record<string, unknown>;
      writes.push({ method: request.method, path, body });
      if (rejectNext) {
        rejectNext = false;
        revision++;
        return HttpResponse.json({}, { status: 412 });
      }
      if (path.endsWith('/timer') && request.method === 'POST')
        timer = {
          ...initial,
          id: 8,
          running: true,
          minutes: null,
          startedAt: new Date().toISOString(),
          version: 0,
        };
      else if (path.endsWith('/timer/stop')) {
        rows = [...rows, { ...initial, id: 8, minutes: 1 }];
        timer = null;
      } else if (path.endsWith('/time-entries') && request.method === 'POST')
        rows = [
          ...rows,
          {
            ...initial,
            id: 6,
            minutes: Number(body.minutes),
            spentOn: String(body.spentOn),
            note: String(body.note),
          },
        ];
      else if (path.endsWith('/time-entries/4') && request.method === 'PUT')
        rows = [{ ...initial, minutes: Number(body.minutes), version: 3 }];
      return path.includes('/capacity/') ? capacity : (timer ?? rows.at(-1));
    }
    if (failed) return HttpResponse.json({}, { status: 403 });
    if (path.endsWith('/timer')) return timer;
    if (path.includes('/time-entries?'))
      return paged
        ? path.includes('before=4')
          ? {
              items: [{ ...initial, id: 2, note: 'Earlier entry' }],
              nextBefore: null,
            }
          : { items: rows, nextBefore: 4 }
        : { items: [...rows, ...(timer ? [timer] : [])], nextBefore: null };
    if (path.includes('/time-report'))
      return {
        sprintId: 10,
        rows: [
          {
            taskId: 3,
            key: 'TM-3',
            title: task.title,
            status: 'To Do',
            assigneeEmail: user.email,
            estimateMinutes: 120,
            loggedMinutes: 150,
            varianceMinutes: 30,
            variancePercent: 25,
          },
        ],
        estimatedMinutes: 120,
        loggedMinutes: 150,
        loggedOnEstimatedMinutes: 150,
        varianceMinutes: 30,
        variancePercent: 25,
        unestimatedTasks: 0,
      };
    if (path.endsWith('/capacity')) return capacity;
    throw new Error(`Unexpected API ${path}`);
  });
  const view = renderWithProviders(
    <ConfirmProvider>
      <TimeView
        project={{
          ...project,
          ...(archived ? { archivedAt: '2026-11-19' } : {}),
        }}
        user={user}
      />
    </ConfirmProvider>,
    { language, path: '/time?sprint=10' },
  );
  return {
    ...view,
    writes,
    fail: () => {
      failed = true;
    },
  };
}
describe('Time and capacity', () => {
  it('shows the fair variance, scoped export, overbooking and accessible reports', async () => {
    const view = mount();
    await screen.findByRole('table', { name: 'Estimated vs actual' });
    expect(
      screen.getByRole('link', { name: 'Export time report (CSV)' }),
    ).toHaveAttribute('href', '/api/projects/1/time-report.csv?sprintId=10');
    expect(await screen.findByText('Overbooked')).toBeVisible();
    expect(
      screen.getByText('The team has more estimated work than available time.'),
    ).toBeVisible();
    expect(screen.getByText('Pairing')).toBeVisible();
    await expectNoSeriousA11yViolations(view.container);
  });
  it('logs minutes through a retained draft and starts and stops the caller’s timer', async () => {
    const user = userEvent.setup();
    const view = mount();
    await user.click(await screen.findByRole('button', { name: 'Log time' }));
    let dialog = screen.getByRole('dialog', { name: 'Log time' });
    await user.selectOptions(
      within(dialog).getByRole('combobox', { name: 'Task' }),
      '3',
    );
    await user.clear(within(dialog).getByLabelText('Minutes'));
    await user.type(within(dialog).getByLabelText('Minutes'), '75');
    await user.clear(within(dialog).getByLabelText('Day worked'));
    await user.type(within(dialog).getByLabelText('Day worked'), '2026-11-18');
    await expectNoSeriousA11yViolations(view.container);
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));
    await waitFor(() =>
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument(),
    );
    expect(view.writes[0]?.body).toEqual({
      taskId: 3,
      minutes: 75,
      spentOn: '2026-11-18',
      note: '',
    });
    await user.click(screen.getByRole('button', { name: 'Start timer' }));
    dialog = screen.getByRole('dialog', { name: 'Start timer' });
    await user.selectOptions(
      within(dialog).getByRole('combobox', { name: 'Task' }),
      '3',
    );
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));
    await user.click(await screen.findByRole('button', { name: 'Stop timer' }));
    await waitFor(() =>
      expect(
        screen.queryByRole('button', { name: 'Stop timer' }),
      ).not.toBeInTheDocument(),
    );
    expect(view.writes.at(-1)?.path).toBe('/api/projects/1/timer/stop');
  });
  it('preserves an estimate draft after a version conflict', async () => {
    const user = userEvent.setup();
    mount({ reject: true });
    await user.click(
      await screen.findByRole('button', { name: 'Set time estimate' }),
    );
    const dialog = screen.getByRole('dialog', { name: 'Set time estimate' });
    const input = within(dialog).getByLabelText('Estimate (minutes)');
    await user.clear(input);
    await user.type(input, '240');
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));
    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      'Your draft is preserved',
    );
    expect(input).toHaveValue(240);
  });
  it('sends the captured revision when correcting an entry', async () => {
    const user = userEvent.setup();
    mount();
    let revision: string | null = null;
    server.use(
      http.put('*/api/projects/1/time-entries/4', async ({ request }) => {
        revision = request.headers.get('If-Match');
        return HttpResponse.json(initial);
      }),
    );
    await user.click(
      await screen.findByRole('button', { name: 'Correct entry' }),
    );
    const dialog = screen.getByRole('dialog', { name: 'Correct entry' });
    await user.clear(within(dialog).getByLabelText('Minutes'));
    await user.type(within(dialog).getByLabelText('Minutes'), '80');
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(revision).toBe('"2"'));
  });
  it('validates days off before saving capacity and preserves a rejected draft', async () => {
    const user = userEvent.setup();
    const view = mount({ reject: true });
    await user.click(
      await screen.findByRole('button', { name: 'Set capacity' }),
    );
    const dialog = screen.getByRole('dialog', { name: 'Set capacity' });
    const days = within(dialog).getByLabelText('Days off (one date per line)');
    await user.type(days, '2026-02-30');
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));
    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      'Enter valid',
    );
    expect(view.writes).toHaveLength(0);
    await user.clear(days);
    await user.type(days, '2026-11-18\n2026-11-19');
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));
    await waitFor(() =>
      expect(within(dialog).getByRole('alert')).toHaveTextContent(
        'Your draft is preserved',
      ),
    );
    expect(days).toHaveValue('2026-11-18\n2026-11-19');
    expect(view.writes[0]?.body.daysOff).toEqual(['2026-11-18', '2026-11-19']);
  });
  it.each([
    { role: 'Viewer', archived: false },
    { role: 'Owner', archived: true },
  ])(
    'keeps $role archived=$archived reads available without mutation controls',
    async (options) => {
      const view = mount({ ...options, language: 'SV' });
      await screen.findByRole('table', { name: 'Uppskattat mot faktiskt' });
      await screen.findByText('Överbokad');
      expect(
        screen.queryByRole('button', { name: 'Logga tid' }),
      ).not.toBeInTheDocument();
      expect(
        screen.queryByRole('button', { name: 'Ange kapacitet' }),
      ).not.toBeInTheDocument();
      expect(
        screen.queryByRole('button', { name: 'Ange tidsuppskattning' }),
      ).not.toBeInTheDocument();
      await expectNoSeriousA11yViolations(view.container);
    },
  );
  it('pages entries and hides cached exports and results after losing access', async () => {
    const user = userEvent.setup();
    const view = mount({ paged: true });
    await user.click(
      await screen.findByRole('button', { name: 'Load more entries' }),
    );
    expect(await screen.findByText('Earlier entry')).toBeVisible();
    view.fail();
    await act(async () => {
      await view.client.invalidateQueries({ queryKey: timeKey(1) });
    });
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Could not load time',
    );
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    expect(
      screen.queryByRole('link', { name: 'Export time report (CSV)' }),
    ).not.toBeInTheDocument();
  });
});
