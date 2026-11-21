import { fireEvent, screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { Route, Routes } from 'react-router';
import {
  afterAll,
  afterEach,
  beforeAll,
  beforeEach,
  expect,
  it,
  vi,
} from 'vitest';
import { clearCsrfToken } from '../../api/client';
import { renderWithProviders } from '../../test/render';
import OrganizationView from './OrganizationView';
import type { Organization } from './queries';

const organization: Organization = {
  id: 3,
  slug: 'platform',
  name: 'Platform group',
  role: 'Owner',
  personal: false,
};
const server = setupServer(
  http.get('/api/auth/csrf', () =>
    HttpResponse.json({ token: 'fixture', headerName: 'X-CSRF-TOKEN' }),
  ),
  http.get('/api/organizations', () => HttpResponse.json([organization])),
  http.get('/api/organizations/platform/invites', () => HttpResponse.json([])),
  http.get('/api/organizations/platform/teams', () => HttpResponse.json([])),
);

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
beforeEach(() => clearCsrfToken());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

function renderRoute(path: string) {
  return renderWithProviders(
    <Routes>
      <Route
        path="o/:org/:section?"
        element={
          <OrganizationView
            onProjectsChanged={async () => {}}
            onOpenProject={vi.fn()}
          />
        }
      />
    </Routes>,
    { path },
  );
}

it('creates a link for an email-bound invitation within the selected organization', async () => {
  const submit = vi.fn();
  server.use(
    http.post('/api/organizations/platform/invites', async ({ request }) => {
      submit(await request.json(), request.headers.get('X-CSRF-TOKEN'));
      return HttpResponse.json(
        {
          invitation: {
            id: 7,
            email: 'peer@example.test',
            role: 'Viewer',
            expiresAt: '2026-11-28T12:00:00Z',
          },
          token: 'fixture-invitation',
        },
        { status: 201 },
      );
    }),
  );
  renderRoute('/o/platform/invitations');
  fireEvent.change(await screen.findByLabelText('Invite email'), {
    target: { value: 'peer@example.test' },
  });
  fireEvent.change(screen.getByLabelText('Role'), {
    target: { value: 'Viewer' },
  });
  fireEvent.click(
    screen.getByRole('button', { name: 'Create invitation link' }),
  );
  const link = await screen.findByLabelText('Invitation link');
  expect(link).toHaveValue(
    `${window.location.origin}/o/platform/invite#token=fixture-invitation`,
  );
  expect(submit).toHaveBeenCalledWith(
    { email: 'peer@example.test', role: 'Viewer' },
    'fixture',
  );
});

it('shows organization teams without mutation controls to a viewer', async () => {
  server.use(
    http.get('/api/organizations', () =>
      HttpResponse.json([{ ...organization, role: 'Viewer' }]),
    ),
  );
  renderRoute('/o/platform/teams');
  await waitFor(() => expect(screen.getByText('No teams yet.')).toBeVisible());
  expect(
    screen.queryByRole('button', { name: 'New team' }),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole('link', { name: 'Invitations' }),
  ).not.toBeInTheDocument();
});
