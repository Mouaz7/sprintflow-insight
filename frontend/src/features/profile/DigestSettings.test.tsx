import { act, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { mockApi } from '../../test/api';
import { server } from '../../test/server';
import { renderWithProviders } from '../../test/render';
import { expectNoSeriousA11yViolations } from '../../test/axe';
import {
  DigestSettings,
  digestQuery,
  type DigestPreferences,
} from './DigestSettings';
const initial: DigestPreferences = {
  frequency: 'OFF',
  timeZone: 'UTC',
  version: 0,
  nextDueAt: null,
  lastSentAt: null,
  mailConfigured: false,
};
function mock(reject = false) {
  let value = { ...initial };
  let failed = reject;
  const writes: unknown[] = [];
  mockApi((path, request) => {
    if (path === '/api/auth/csrf')
      return { headerName: 'X-CSRF-TOKEN', token: 'test' };
    if (path === '/api/auth/digest') {
      if (request.method === 'PUT') {
        writes.push(JSON.parse(request.body ?? '{}'));
        if (failed) {
          failed = false;
          value = {
            ...initial,
            frequency: 'WEEKLY',
            timeZone: 'Europe/Stockholm',
            version: 1,
          };
          return HttpResponse.json({}, { status: 412 });
        }
        value = {
          ...value,
          ...JSON.parse(request.body ?? '{}'),
          version: value.version + 1,
        };
      }
      return value;
    }
    throw new Error(path);
  });
  return { writes, value: () => value };
}
it('starts off, shows missing SMTP and saves only the digest preference with its revision', async () => {
  const state = mock();
  const keyboard = userEvent.setup();
  const view = renderWithProviders(<DigestSettings />);
  let revision: string | null = null;
  server.use(
    http.put('*/api/auth/digest', async ({ request }) => {
      revision = request.headers.get('If-Match');
      return HttpResponse.json({
        ...initial,
        ...((await request.json()) as object),
        version: 1,
      });
    }),
  );
  expect(
    await screen.findByRole('combobox', { name: 'Summary frequency' }),
  ).toHaveValue('OFF');
  expect(state.writes).toHaveLength(0);
  expect(screen.getByText(/Email is not configured/)).toBeVisible();
  await keyboard.selectOptions(
    screen.getByRole('combobox', { name: 'Summary frequency' }),
    'DAILY',
  );
  const zone = screen.getByRole('combobox', { name: 'Time zone (IANA)' });
  await keyboard.clear(zone);
  await keyboard.type(zone, 'Europe/Stockholm');
  await keyboard.click(
    screen.getByRole('button', { name: 'Save email summaries' }),
  );
  expect(await screen.findByRole('status')).toHaveTextContent('settings saved');
  expect(revision).toBe('"0"');
  await expectNoSeriousA11yViolations(view.container);
});
it('retains a stale draft and requires an explicit reload before saving another revision', async () => {
  mock(true);
  const keyboard = userEvent.setup();
  renderWithProviders(<DigestSettings />);
  await keyboard.selectOptions(
    await screen.findByRole('combobox', { name: 'Summary frequency' }),
    'DAILY',
  );
  await keyboard.click(
    screen.getByRole('button', { name: 'Save email summaries' }),
  );
  expect(await screen.findByRole('alert')).toHaveTextContent(
    'Your draft is still here',
  );
  expect(
    screen.getByRole('combobox', { name: 'Summary frequency' }),
  ).toHaveValue('DAILY');
  expect(
    screen.getByRole('button', { name: 'Save email summaries' }),
  ).toBeDisabled();
  await keyboard.click(
    screen.getByRole('button', { name: 'Load saved settings' }),
  );
  await waitFor(() =>
    expect(
      screen.getByRole('combobox', { name: 'Summary frequency' }),
    ).toHaveValue('WEEKLY'),
  );
  expect(
    screen.getByRole('combobox', { name: 'Time zone (IANA)' }),
  ).toHaveValue('Europe/Stockholm');
});
it('supports Swedish settings and hides cached preferences when the session is rejected', async () => {
  mock();
  const view = renderWithProviders(<DigestSettings />, { language: 'SV' });
  await screen.findByRole('heading', { name: 'E-postsammanfattningar' });
  await expectNoSeriousA11yViolations(view.container);
  server.use(
    http.get('*/api/auth/digest', () => HttpResponse.json({}, { status: 401 })),
  );
  await act(async () => {
    await view.client.invalidateQueries({ queryKey: digestQuery.queryKey });
  });
  expect(
    await screen.findByText(
      'Kunde inte ladda inställningarna för e-postsammanfattningar.',
    ),
  ).toBeVisible();
  expect(
    screen.queryByRole('button', { name: 'Spara e-postsammanfattningar' }),
  ).not.toBeInTheDocument();
});
