import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, it } from 'vitest';
import { HttpResponse } from 'msw';
import { mockApi } from '../../test/api';
import { renderWithProviders } from '../../test/render';
import { expectNoSeriousA11yViolations } from '../../test/axe';
import { DigestSettings } from './DigestSettings';
import { UnsubscribeView } from './UnsubscribeView';
it('does not unsubscribe on navigation and requires a deliberate confirmation without signing in', async () => {
  const writes: unknown[] = [];
  mockApi((path, request) => {
    if (path === '/api/auth/csrf')
      return { headerName: 'X-CSRF-TOKEN', token: 'test' };
    if (path === '/api/digest/unsubscribe') {
      writes.push(JSON.parse(request.body ?? '{}'));
      return new HttpResponse(null, { status: 204 });
    }
    throw new Error(path);
  });
  const keyboard = userEvent.setup();
  const view = renderWithProviders(<UnsubscribeView />, {
    path: '/email-preferences?token=opaque-capability',
  });
  expect(writes).toHaveLength(0);
  await expectNoSeriousA11yViolations(view.container);
  await keyboard.click(
    screen.getByRole('button', { name: 'Stop email summaries' }),
  );
  expect(await screen.findByRole('status')).toHaveTextContent(
    'have been stopped',
  );
  expect(writes).toEqual([{ token: 'opaque-capability' }]);
});
it('shows an invalid-link response in Swedish without exposing account details', async () => {
  mockApi((path) =>
    path === '/api/auth/csrf'
      ? { headerName: 'X-CSRF-TOKEN', token: 'test' }
      : HttpResponse.json({}, { status: 400 }),
  );
  const keyboard = userEvent.setup();
  renderWithProviders(<UnsubscribeView />, {
    language: 'SV',
    path: '/email-preferences?token=expired',
  });
  await keyboard.click(
    screen.getByRole('button', { name: 'Stoppa e-postsammanfattningar' }),
  );
  expect(await screen.findByRole('alert')).toHaveTextContent(
    'ogiltig eller har gått ut',
  );
});
it('does not submit an absent token', () => {
  renderWithProviders(<UnsubscribeView />, { language: 'EN' });
  expect(
    screen.queryByRole('button', { name: 'Stop email summaries' }),
  ).not.toBeInTheDocument();
  expect(screen.getByRole('alert')).toHaveTextContent('invalid or has expired');
});

it('refreshes a cached digest preference after a confirmed opt-out', async () => {
  let frequency = 'DAILY';
  mockApi((path) => {
    if (path === '/api/auth/csrf')
      return { headerName: 'X-CSRF-TOKEN', token: 'test' };
    if (path === '/api/auth/digest')
      return {
        frequency,
        timeZone: 'UTC',
        version: 1,
        nextDueAt: null,
        lastSentAt: null,
        mailConfigured: false,
      };
    if (path === '/api/digest/unsubscribe') {
      frequency = 'OFF';
      return new HttpResponse(null, { status: 204 });
    }
    throw new Error(path);
  });
  const keyboard = userEvent.setup();
  renderWithProviders(
    <>
      <DigestSettings />
      <UnsubscribeView />
    </>,
    { language: 'EN', path: '/email-preferences?token=valid' },
  );
  const cadence = await screen.findByRole('combobox', {
    name: 'Summary frequency',
  });
  expect(cadence).toHaveValue('DAILY');
  await keyboard.click(
    screen.getByRole('button', { name: 'Stop email summaries' }),
  );
  await screen.findByText('Email summaries have been stopped.');
  expect(cadence).toHaveValue('OFF');
});
