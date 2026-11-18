import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { FlowReport, Project, WipSettings } from '../../api/contracts';
import { mockApi } from '../../test/api';
import { renderWithProviders } from '../../test/render';
import { expectNoSeriousA11yViolations } from '../../test/axe';
import { ConfirmProvider } from '../../shared/ui/ui';
import { FlowView } from './FlowView';

const project: Project = { id: 1, name: 'Flow', ownerEmail: 'owner@test.se' };
export const report: FlowReport = {
  days: 30,
  start: '2026-10-01',
  end: '2026-10-30',
  generatedAt: '2026-10-30T12:00:00Z',
  cumulativeFlow: Array.from({ length: 8 }, (_, i) => ({
    date: `2026-10-0${i + 1}`,
    toDo: 1,
    inProgress: 2,
    review: 0,
    done: i + 1,
  })),
  cycleTimeHours: { count: 8, mean: 15, p50: 10, p85: 10, p95: 36 },
  leadTimeHours: { count: 8, mean: 39, p50: 34, p85: 34, p95: 60 },
  completedTasks: 8,
  throughputPerDay: 0.27,
  completions: Array.from({ length: 8 }, (_, i) => ({
    taskId: i + 1,
    key: `QA-${i + 1}`,
    completedAt: `2026-10-0${i + 1}T12:00:00Z`,
    cycleTimeHours: i === 7 ? 50 : 10,
    leadTimeHours: i === 7 ? 74 : 34,
  })),
  agingWip: [
    {
      taskId: 9,
      key: 'QA-9',
      title: 'Old work',
      status: 'In Progress',
      ageHours: 72,
      statusAgeHours: 24,
      band: 'ABOVE_P95',
    },
  ],
  columns: [
    {
      status: 'To Do',
      currentWip: 1,
      averageWip: 1,
      limit: null,
      overLimit: false,
      bottleneck: null,
    },
    {
      status: 'In Progress',
      currentWip: 2,
      averageWip: 1.5,
      limit: 1,
      overLimit: true,
      bottleneck: 'OVER_LIMIT',
    },
    {
      status: 'Review',
      currentWip: 0,
      averageWip: 0.5,
      limit: null,
      overLimit: false,
      bottleneck: null,
    },
  ],
  controlChart: {
    available: true,
    minimumPoints: 6,
    mean: 15,
    upperLimit: 30.2,
    lowerLimit: 0,
    movingRangeMean: 5.71,
    upperRangeLimit: 18.67,
    signalCount: 1,
    points: Array.from({ length: 8 }, (_, i) => ({
      taskId: i + 1,
      key: `QA-${i + 1}`,
      completedAt: `2026-10-0${i + 1}T12:00:00Z`,
      cycleTimeHours: i === 7 ? 50 : 10,
      movingRange: i === 0 ? null : i === 7 ? 40 : 0,
      signals: i === 7 ? ['OUTSIDE_LIMITS', 'LARGE_MOVING_RANGE'] : [],
    })),
  },
  littlesLaw: {
    status: 'INCONSISTENT',
    averageWip: 2,
    throughputPerDay: 0.27,
    averageCycleTimeDays: 0.625,
    expectedWip: 0.17,
    ratio: 11.85,
    tolerance: 0.25,
  },
  tasksWithoutHistory: 1,
};

function mount({
  role = 'Owner',
  archived = false,
  language = 'EN' as 'EN' | 'SV',
  data = report,
  stale = false,
} = {}) {
  const reads: string[] = [];
  const writes: { path: string; body: unknown }[] = [];
  let settings: WipSettings = {
    version: 4,
    limits: [
      { status: 'To Do', limit: null },
      { status: 'In Progress', limit: 1 },
      { status: 'Review', limit: null },
    ],
  };
  mockApi((path, request) => {
    if (path === '/api/auth/csrf')
      return { token: 'test', headerName: 'X-CSRF-TOKEN' };
    if (request.method !== 'GET') {
      writes.push({ path, body: JSON.parse(request.body ?? '{}') });
      if (stale)
        return HttpResponse.json({ detail: 'Stale settings' }, { status: 412 });
      const body = JSON.parse(request.body ?? '{}') as Pick<
        WipSettings,
        'limits'
      >;
      settings = { version: 5, limits: body.limits };
      return settings;
    }
    reads.push(path);
    if (path.endsWith('/role')) return { role };
    if (path.endsWith('/wip-limits')) return settings;
    if (path.includes('/flow?')) return data;
    throw new Error(`Unexpected API path: ${path}`);
  });
  const view = renderWithProviders(
    <ConfirmProvider>
      <FlowView
        project={{
          ...project,
          ...(archived ? { archivedAt: '2026-10-30' } : {}),
        }}
      />
    </ConfirmProvider>,
    { language, path: '/flow' },
  );
  return { ...view, reads, writes };
}

describe('Flow analysis', () => {
  it('shows actual distributions, signals, missing-history coverage and accessible chart alternatives', async () => {
    const { container } = mount();
    await screen.findByRole('heading', {
      name: 'Cumulative flow',
    });
    expect(screen.getByText(/1 task lacks usable history/)).toBeVisible();
    expect(screen.getByText('Over the WIP limit')).toBeVisible();
    expect(screen.getByText(/Outside the three-sigma limits/)).toBeVisible();
    expect(
      screen.getByRole('link', { name: 'QA-9 · Old work' }),
    ).toHaveAttribute('href', '/tasks?task=QA-9');
    expect(
      screen.getByRole('heading', { name: 'Review the boundary and history' }),
    ).toBeVisible();
    fireEvent.click(screen.getAllByText('View data as a table')[0]!);
    expect(screen.getAllByRole('table')[0]).toBeVisible();
    await expectNoSeriousA11yViolations(container);
  });

  it('fetches the selected window through the route and formats Swedish values', async () => {
    const { reads } = mount({ language: 'SV' });
    await screen.findByRole('heading', {
      name: 'Kumulativt flöde',
    });
    fireEvent.change(screen.getByLabelText('Historikperiod'), {
      target: { value: '90' },
    });
    await waitFor(() =>
      expect(reads).toContain('/api/projects/1/flow?days=90'),
    );
    expect(screen.getByText('0,27')).toBeVisible();
    expect(screen.getByText('Över WIP-gränsen')).toBeVisible();
  });

  it.each([
    { role: 'Member', archived: false },
    { role: 'Viewer', archived: false },
    { role: 'Owner', archived: true },
  ])(
    'keeps $role archived=$archived readable without exposing WIP edits',
    async (options) => {
      mount(options);
      await screen.findByRole('heading', {
        name: 'Cumulative flow',
      });
      expect(
        screen.queryByRole('button', { name: 'Set WIP limits' }),
      ).not.toBeInTheDocument();
      expect(screen.getByText(/You can read this analysis/)).toBeVisible();
    },
  );

  it('retains a conflicting WIP draft and its selected fields', async () => {
    mount({ stale: true });
    await screen.findByRole('button', { name: 'Set WIP limits' });
    fireEvent.click(screen.getByRole('button', { name: 'Set WIP limits' }));
    const dialog = await screen.findByRole('dialog');
    const input = await within(dialog).findByRole('spinbutton', {
      name: 'In Progress',
    });
    fireEvent.change(input, { target: { value: '3' } });
    fireEvent.click(
      within(dialog).getByRole('button', { name: 'Save limits' }),
    );
    expect(
      await within(dialog).findByText(/Another administrator changed/),
    ).toBeVisible();
    expect(input).toHaveValue(3);
  });

  it('saves optional limits and allows clearing all three columns', async () => {
    const user = userEvent.setup();
    const { writes } = mount();
    await user.click(
      await screen.findByRole('button', { name: 'Set WIP limits' }),
    );
    const dialog = screen.getByRole('dialog');
    await user.clear(
      await within(dialog).findByRole('spinbutton', { name: 'In Progress' }),
    );
    await user.click(
      within(dialog).getByRole('button', { name: 'Save limits' }),
    );
    await waitFor(() =>
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument(),
    );
    expect(writes).toEqual([
      { path: '/api/projects/1/wip-limits', body: { limits: [] } },
    ]);
  });
});
