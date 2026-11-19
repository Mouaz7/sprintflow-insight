import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { expect, it } from 'vitest';
import { mockApi } from '../../test/api';
import { server } from '../../test/server';
import { renderWithProviders } from '../../test/render';
import { expectNoSeriousA11yViolations } from '../../test/axe';
import { DashboardView } from './DashboardView';
import { dashboardKey } from './queries';
import { defaultWidgets, type DashboardLayout } from './contracts';
const project = { id: 1, name: 'Delivery', ownerEmail: 'owner@example.test' };
const user = { id: 1, email: project.ownerEmail };
function mount({
  archived = false,
  language = 'EN',
  empty = false,
  reject = false,
  sparse = false,
}: {
  archived?: boolean;
  language?: 'EN' | 'SV';
  empty?: boolean;
  reject?: boolean;
  sparse?: boolean;
} = {}) {
  let layout: DashboardLayout = {
    version: 0,
    widgets: empty ? [] : defaultWidgets(),
  };
  const reads: string[] = [];
  let fail = reject;
  mockApi((path, request) => {
    if (path === '/api/auth/csrf')
      return { headerName: 'X-CSRF-TOKEN', token: 'test' };
    reads.push(path);
    if (path.endsWith('/dashboard')) {
      if (request.method === 'PUT') {
        if (fail) {
          fail = false;
          layout = { version: 1, widgets: [] };
          return HttpResponse.json({}, { status: 412 });
        }
        layout = {
          version: layout.version + 1,
          ...JSON.parse(request.body ?? '{}'),
        };
      }
      return layout;
    }
    if (path.startsWith('/api/sprints'))
      return [
        {
          id: 10,
          name: 'Current sprint',
          status: 'Active',
          goal: 'Ship an accessible checkout',
          projectId: 1,
        },
        {
          id: 11,
          name: 'Next sprint',
          status: 'Planned',
          goal: 'Next goal',
          projectId: 1,
        },
      ];
    if (path.startsWith('/api/tasks'))
      return [
        {
          id: 1,
          key: 'DB-1',
          title: 'My active task',
          assigneeEmail: user.email,
          status: 'To Do',
        },
        {
          id: 2,
          title: 'Other person task',
          assigneeEmail: 'other@test.se',
          status: 'To Do',
        },
        {
          id: 3,
          title: 'My done task',
          assigneeEmail: user.email,
          status: 'Done',
        },
      ];
    if (path.includes('/metrics'))
      return {
        burndownAvailable: !sparse,
        burndown: [
          { date: '2026-11-18', remainingPoints: 8 },
          { date: '2026-11-19', remainingPoints: 5 },
        ],
      };
    if (path.includes('/flow'))
      return {
        cumulativeFlow: [
          { date: '2026-11-18', toDo: 2, inProgress: 1, review: 0, done: 3 },
        ],
        columns: [
          { status: 'In Progress', currentWip: 3, limit: 2, overLimit: true },
        ],
      };
    if (path.includes('/forecast'))
      return {
        warnings: sparse ? ['NO_HISTORY'] : [],
        completionDates: [
          { confidence: 0.85, date: '2026-12-01' },
          { confidence: 0.95, date: null },
        ],
      };
    throw new Error(path);
  });
  const view = renderWithProviders(
    <DashboardView
      project={{
        ...project,
        ...(archived ? { archivedAt: '2026-11-19T00:00:00Z' } : {}),
      }}
      user={user}
    />,
    { language, path: '/dashboard' },
  );
  return { ...view, reads, layout: () => layout };
}
it('uses all six real data widgets, filters own open work and offers chart tables', async () => {
  const view = mount();
  await screen.findByText('Ship an accessible checkout');
  await screen.findByRole('link', { name: 'DB-1 · My active task' });
  expect(
    screen.getByRole('link', { name: 'DB-1 · My active task' }),
  ).toHaveAttribute('href', '/tasks?task=DB-1');
  expect(screen.queryByText('Other person task')).not.toBeInTheDocument();
  expect(screen.queryByText('My done task')).not.toBeInTheDocument();
  expect(await screen.findByText('Over WIP limit')).toBeVisible();
  expect(await screen.findByText('Confidence 85%')).toBeVisible();
  expect(screen.getByText('Beyond the forecast horizon')).toBeVisible();
  expect(document.querySelectorAll('.dashboard-card')).toHaveLength(6);
  await expectNoSeriousA11yViolations(view.container);
});
it('saves chosen widget order and widths with the revision and returns focus', async () => {
  const keyboard = userEvent.setup();
  const view = mount();
  let header: string | null = null;
  server.use(
    http.put('*/api/projects/1/dashboard', async ({ request }) => {
      header = request.headers.get('If-Match');
      return HttpResponse.json({
        version: 1,
        ...((await request.json()) as object),
      });
    }),
  );
  const trigger = await screen.findByRole('button', {
    name: 'Customize dashboard',
  });
  await keyboard.click(trigger);
  const editor = screen.getByRole('dialog', { name: 'Customize dashboard' });
  await keyboard.click(
    within(editor).getByRole('button', { name: 'Move up: Cumulative flow' }),
  );
  await keyboard.click(
    within(editor).getByRole('checkbox', {
      name: 'Delivery forecast',
    }),
  );
  await keyboard.click(
    within(editor).getAllByRole('checkbox', { name: 'Wide card' })[0]!,
  );
  await expectNoSeriousA11yViolations(editor);
  await keyboard.click(
    within(editor).getByRole('button', { name: 'Save dashboard' }),
  );
  await waitFor(() =>
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument(),
  );
  expect(header).toBe('"0"');
  expect(document.querySelector('.dashboard-card')).toHaveAttribute(
    'aria-label',
    'Cumulative flow',
  );
  expect(document.querySelector('.dashboard-card')).not.toHaveClass(
    'dashboard-wide',
  );
  expect(document.querySelectorAll('.dashboard-card')).toHaveLength(5);
  await waitFor(() => expect(trigger).toHaveFocus());
  expect(
    view.client.getQueryData<DashboardLayout>(dashboardKey(1))?.version,
  ).toBe(1);
});
it('keeps a stale draft visible until the user closes and reopens the editor', async () => {
  const keyboard = userEvent.setup();
  mount({ reject: true });
  await keyboard.click(
    await screen.findByRole('button', { name: 'Customize dashboard' }),
  );
  let editor = screen.getByRole('dialog');
  await keyboard.click(
    within(editor).getByRole('checkbox', {
      name: 'Delivery forecast',
    }),
  );
  await keyboard.click(
    within(editor).getByRole('button', { name: 'Save dashboard' }),
  );
  expect(await screen.findByRole('alert')).toHaveTextContent(
    'Your draft is still here',
  );
  expect(
    within(editor).getByRole('checkbox', {
      name: 'Delivery forecast',
    }),
  ).not.toBeChecked();
  expect(
    within(editor).getByRole('button', { name: 'Save dashboard' }),
  ).toBeDisabled();
  await keyboard.click(within(editor).getByRole('button', { name: 'Cancel' }));
  // Drawer confirms discarding the unsaved draft.
  const confirm = screen.queryByRole('button', { name: 'Discard changes' });
  if (confirm) await keyboard.click(confirm);
  await waitFor(() =>
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument(),
  );
  await keyboard.click(
    screen.getByRole('button', { name: 'Customize dashboard' }),
  );
  editor = screen.getByRole('dialog');
  expect(
    within(editor)
      .getAllByRole('checkbox')
      .filter((c) => (c as HTMLInputElement).checked),
  ).toHaveLength(0);
});
it('saves an empty layout and can restore the defaults explicitly', async () => {
  const keyboard = userEvent.setup();
  const view = mount({ empty: true });
  expect(
    await screen.findByText('Your dashboard has no widgets.'),
  ).toBeVisible();
  expect(
    view.reads.some(
      (p) =>
        p.includes('/flow') || p.includes('/forecast') || p.includes('/tasks'),
    ),
  ).toBe(false);
  await keyboard.click(
    screen.getByRole('button', { name: 'Customize dashboard' }),
  );
  await keyboard.click(
    screen.getByRole('button', { name: 'Use default layout' }),
  );
  await keyboard.click(screen.getByRole('button', { name: 'Save dashboard' }));
  await waitFor(() => expect(view.layout().widgets).toHaveLength(6));
});
it('shows Swedish archived and missing-history states accessibly', async () => {
  const view = mount({ language: 'SV', archived: true, sparse: true });
  await screen.findByText('Ship an accessible checkout');
  expect(
    screen.queryByRole('button', { name: 'Anpassa dashboard' }),
  ).not.toBeInTheDocument();
  expect(
    await screen.findByText(
      'Mer historik över slutfört arbete behövs för en leveransprognos.',
    ),
  ).toBeVisible();
  await expectNoSeriousA11yViolations(view.container);
});
it('uses the chosen sprint for burndown and goal while flow stays project-wide', async () => {
  const keyboard = userEvent.setup();
  const view = mount();
  await screen.findByText('Ship an accessible checkout');
  await keyboard.selectOptions(
    screen.getByRole('combobox', { name: 'Sprint for burndown and goal' }),
    '11',
  );
  expect(await screen.findByText('Next goal')).toBeVisible();
  expect(view.reads).toContain('/api/projects/1/metrics?sprintId=11');
  expect(
    view.reads
      .filter((path) => path.includes('/flow'))
      .every((path) => path.endsWith('days=30')),
  ).toBe(true);
});
it('hides cached widgets when dashboard access is revoked', async () => {
  const view = mount();
  await screen.findByText('Ship an accessible checkout');
  server.use(
    http.get('*/api/projects/1/dashboard', () =>
      HttpResponse.json({}, { status: 403 }),
    ),
  );
  await act(async () => {
    await view.client.invalidateQueries({ queryKey: dashboardKey(1) });
  });
  expect(
    await screen.findByText('Could not load your dashboard. Try again.'),
  ).toBeVisible();
  expect(
    screen.queryByText('Ship an accessible checkout'),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole('button', { name: 'Customize dashboard' }),
  ).not.toBeInTheDocument();
});
