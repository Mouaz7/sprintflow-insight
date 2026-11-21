import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, beforeEach, expect, it } from 'vitest';
import { clearCsrfToken } from '../../api/client';
import { renderWithProviders } from '../../test/render';
import AutomationView from './AutomationView';
import type { Draft, GithubRules, Rule } from './queries';

const project = {
  id: 1,
  name: 'Automation fixture',
  ownerEmail: 'owner@example.test',
};
let saved: Rule[] = [];
let submitted: Draft | undefined;
const server = setupServer(
  http.get('/api/auth/csrf', () =>
    HttpResponse.json({ token: 'fixture', headerName: 'X-CSRF-TOKEN' }),
  ),
  http.get('/api/projects/1/role', () => HttpResponse.json({ role: 'Owner' })),
  http.get('/api/projects/1/automation/rules', () => HttpResponse.json(saved)),
  http.get('/api/projects/1/automation/runs', () => HttpResponse.json([])),
  http.get(
    '/api/projects/1/automation/github',
    () => new HttpResponse(null, { status: 204 }),
  ),
  http.post('/api/projects/1/automation/rules', async ({ request }) => {
    submitted = (await request.json()) as Draft;
    const rule: Rule = {
      ...submitted,
      id: 9,
      trigger: 'TASK_MOVED',
      version: 0,
    };
    saved = [rule];
    return HttpResponse.json(rule, { status: 201 });
  }),
);

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
beforeEach(() => {
  saved = [];
  submitted = undefined;
  clearCsrfToken();
});
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

it('submits a visual rule as DSL and refreshes the project rule list', async () => {
  renderWithProviders(<AutomationView project={project} />);
  fireEvent.click(await screen.findByRole('button', { name: 'New rule' }));
  const editor = within(screen.getByRole('dialog', { name: 'New rule' }));
  fireEvent.change(editor.getByLabelText('Rule name'), {
    target: { value: 'Label review work' },
  });
  fireEvent.change(editor.getByLabelText('Action value'), {
    target: { value: 'ready' },
  });
  fireEvent.click(editor.getByRole('button', { name: 'Save rule' }));
  expect(
    await screen.findByRole('heading', { name: 'Label review work' }),
  ).toBeVisible();
  await waitFor(() =>
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument(),
  );
  expect(submitted).toEqual({
    name: 'Label review work',
    enabled: true,
    source: 'when task-moved to "Review"\nthen label "ready"',
  });
});

it('shows the log to an owner of an archived project without write controls', async () => {
  renderWithProviders(
    <AutomationView
      project={{ ...project, archivedAt: '2026-11-01T00:00:00Z' }}
    />,
  );
  expect(await screen.findByRole('heading', { name: 'Run log' })).toBeVisible();
  expect(
    screen.queryByRole('button', { name: 'New rule' }),
  ).not.toBeInTheDocument();
});

it('updates GitHub status rules on the existing connection and reads delivery history', async () => {
  let configuration: GithubRules = { reviewOnOpen: false, doneOnMerge: false };
  server.use(
    http.get('/api/projects/1/automation/github', () =>
      HttpResponse.json({
        endpointId: 'registered-endpoint',
        repository: 'owner/repo',
        ...configuration,
      }),
    ),
    http.put('/api/projects/1/automation/github/rules', async ({ request }) => {
      configuration = (await request.json()) as GithubRules;
      return new HttpResponse(null, { status: 204 });
    }),
    http.get('/api/projects/1/automation/github/deliveries', () =>
      HttpResponse.json([
        {
          id: 'delivery',
          repository: 'owner/repo',
          event: 'push',
          action: null,
          linkedTasks: 1,
          movedTasks: 0,
          receivedAt: '2026-11-21T12:00:00Z',
        },
      ]),
    ),
  );
  renderWithProviders(<AutomationView project={project} />);
  const disclosure = await screen.findByText('GitHub webhook', { exact: true });
  fireEvent.click(disclosure);
  fireEvent.click(
    await screen.findByRole('checkbox', { name: /Move to Review/ }),
  );
  fireEvent.click(screen.getByRole('button', { name: 'Save GitHub rules' }));
  await waitFor(() =>
    expect(configuration).toEqual({ reviewOnOpen: true, doneOnMerge: false }),
  );
  expect(await screen.findByText(/1 task linked, 0 moved/)).toBeVisible();
});
