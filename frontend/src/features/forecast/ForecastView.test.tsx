import { HttpResponse } from 'msw';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, it } from 'vitest';
import type { ForecastReport } from '../../api/contracts';
import { mockApi } from '../../test/api';
import { renderWithProviders } from '../../test/render';
import { expectNoSeriousA11yViolations } from '../../test/axe';
import { ForecastView } from './ForecastView';

const report: ForecastReport = {
  basis: 'THROUGHPUT',
  unit: 'ITEMS',
  periodDays: 7,
  start: '2026-11-18',
  seed: '18446744073709551615',
  simulations: 10000,
  minimumPeriods: 6,
  history: Array.from({ length: 6 }, (_, i) => ({
    start: `2026-10-${String(i + 1).padStart(2, '0')}`,
    end: `2026-10-${String(i + 2).padStart(2, '0')}`,
    count: 7,
    label: null,
  })),
  warnings: [],
  scope: {
    epicId: null,
    epicTitle: null,
    openTasks: 10,
    openStoryPoints: 20,
    unplannedStories: 0,
    zeroPointTasks: 0,
    tasksWithoutHistory: 0,
  },
  remaining: 10,
  completionDates: [0.5, 0.85, 0.95].map((confidence) => ({
    confidence,
    date: '2026-11-28',
  })),
  completionHistogram: [{ from: 10, to: 10, count: 10000 }],
  beyondHorizon: 0,
  horizonDays: 1825,
  itemsByDate: {
    date: '2026-12-02',
    source: 'DEFAULT',
    items: [
      { confidence: 0.5, items: 14 },
      { confidence: 0.85, items: 12 },
      { confidence: 0.95, items: 10 },
    ],
    histogram: [
      { from: 10, to: 12, count: 4000 },
      { from: 13, to: 15, count: 6000 },
    ],
  },
  target: {
    items: 10,
    date: '2026-12-02',
    source: 'DEFAULT',
    probability: 0.85,
  },
};
function mount(
  data: ForecastReport = report,
  language: 'EN' | 'SV' = 'EN',
  path = '/forecast',
) {
  const reads: string[] = [];
  mockApi((url, request) => {
    expect(request.method).toBe('GET');
    reads.push(url);
    if (url.endsWith('/epics'))
      return [{ id: 2, title: 'Customer release', version: 0 }];
    if (url.includes('/forecast')) return data;
    throw new Error(`Unexpected request ${url}`);
  });
  const view = renderWithProviders(
    <ForecastView
      project={{ id: 1, name: 'Delivery', ownerEmail: 'owner@example.test' }}
    />,
    { language, path },
  );
  return { ...view, reads };
}

it('shows coherent date/amount confidence, real histogram tables and accessible guidance', async () => {
  const { container } = mount();
  await screen.findByRole('heading', {
    name: 'When will the remaining work be done?',
  });
  expect(
    screen.getByText('85%', { selector: '.forecast-probability' }),
  ).toBeVisible();
  expect(screen.getAllByRole('figure')).toHaveLength(2);
  fireEvent.click(screen.getAllByText('View data as a table')[0]!);
  expect(
    screen.getByRole('table', { name: 'Simulated days to completion (Days)' }),
  ).toHaveTextContent('10,000');
  await expectNoSeriousA11yViolations(container);
});

it('applies an epic, work amount and deadline through a shareable route and resets unit-dependent drafts', async () => {
  const user = userEvent.setup();
  const { reads } = mount();
  await screen.findByRole('heading', {
    name: 'When will the remaining work be done?',
  });
  await user.selectOptions(
    screen.getByRole('combobox', { name: 'Work scope' }),
    '2',
  );
  await user.type(
    screen.getByRole('spinbutton', { name: 'Remaining work (optional)' }),
    '25',
  );
  const future = new Date(Date.now() + 30 * 86400000)
    .toISOString()
    .slice(0, 10);
  fireEvent.change(
    screen.getByRole('textbox', { name: /^Target date \(optional\)$/ }),
    { target: { value: future } },
  );
  await user.type(
    screen.getByRole('spinbutton', { name: 'Target amount (optional)' }),
    '20',
  );
  await user.click(screen.getByRole('button', { name: 'Update forecast' }));
  await waitFor(() =>
    expect(
      reads.some(
        (url) =>
          url.includes('epicId=2') &&
          url.includes('remaining=25') &&
          url.includes(`targetDate=${future}`) &&
          url.includes('targetItems=20'),
      ),
    ).toBe(true),
  );
  await user.selectOptions(
    screen.getByRole('combobox', { name: 'History basis' }),
    'VELOCITY',
  );
  expect(
    screen.getByRole('spinbutton', { name: 'Remaining work (optional)' }),
  ).toHaveValue(null);
  expect(
    screen.getByRole('spinbutton', { name: 'Target amount (optional)' }),
  ).toHaveValue(null);
  await user.click(
    screen.getByRole('button', { name: 'Use project defaults' }),
  );
  await waitFor(() =>
    expect(screen.getByRole('combobox', { name: 'Work scope' })).toHaveValue(
      '',
    ),
  );
});

it('restores applied query parameters from a deep link', async () => {
  mount(
    report,
    'EN',
    '/forecast?basis=VELOCITY&epicId=2&remaining=30&targetDate=2030-01-01&targetItems=20',
  );
  await screen.findByRole('heading', {
    name: 'When will the remaining work be done?',
  });
  expect(screen.getByRole('combobox', { name: 'History basis' })).toHaveValue(
    'VELOCITY',
  );
  expect(screen.getByRole('combobox', { name: 'Work scope' })).toHaveValue('2');
  expect(
    screen.getByRole('spinbutton', { name: 'Remaining work (optional)' }),
  ).toHaveValue(30);
  expect(
    screen.getByRole('textbox', { name: /^Target date \(optional\)$/ }),
  ).toHaveValue('2030-01-01');
});

it('does not present zero-data simulations as a usable delivery prediction', async () => {
  mount({
    ...report,
    history: [],
    simulations: 0,
    warnings: ['NO_HISTORY'],
    completionDates: report.completionDates.map((row) => ({
      ...row,
      date: null,
    })),
    completionHistogram: [],
    beyondHorizon: 0,
  });
  expect(
    await screen.findByRole('heading', {
      name: 'More delivery history is needed',
    }),
  ).toBeVisible();
  expect(
    screen.queryByRole('heading', {
      name: 'When will the remaining work be done?',
    }),
  ).not.toBeInTheDocument();
  expect(screen.queryAllByRole('figure')).toHaveLength(0);
});

it('explains small samples, missing work, conditional epic capacity and censored outcomes in Swedish', async () => {
  mount(
    {
      ...report,
      warnings: [
        'INSUFFICIENT_HISTORY',
        'MISSING_TASK_HISTORY',
        'UNPLANNED_STORIES',
        'ZERO_POINT_TASKS',
        'EPIC_USES_PROJECT_HISTORY',
      ],
      scope: {
        ...report.scope,
        epicId: 2,
        epicTitle: 'Customer release',
        tasksWithoutHistory: 2,
        unplannedStories: 1,
      },
      beyondHorizon: 200,
    },
    'SV',
  );
  expect(await screen.findByText(/Färre än 6 hela perioder/)).toBeVisible();
  expect(
    screen.getByText(/2 uppgifter saknar användbar historik/),
  ).toBeVisible();
  expect(screen.getByText(/1 story saknar uppgifter/)).toBeVisible();
  expect(screen.getByText(/hela projektets leveranshistorik/)).toBeVisible();
  expect(screen.getByText(/200 simuleringar avslutades inte/)).toBeVisible();
  expect(screen.getByRole('heading', { name: 'Prognoser' })).toBeVisible();
});

it('shows a correctable input error while keeping query controls available', async () => {
  mockApi((url) =>
    url.endsWith('/epics')
      ? []
      : HttpResponse.json({ detail: 'Work amount too large' }, { status: 400 }),
  );
  renderWithProviders(
    <ForecastView
      project={{ id: 1, name: 'Delivery', ownerEmail: 'owner@example.test' }}
    />,
    { language: 'EN', path: '/forecast' },
  );
  expect(await screen.findByText(/If recorded work is larger/)).toBeVisible();
  expect(
    screen.getByRole('spinbutton', { name: 'Remaining work (optional)' }),
  ).toBeEnabled();
});
