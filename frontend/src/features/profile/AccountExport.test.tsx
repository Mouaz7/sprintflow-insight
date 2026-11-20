import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { it, expect, vi } from 'vitest';
import { http, HttpResponse } from 'msw';
import { server } from '../../test/server';
import { renderWithProviders } from '../../test/render';
import { AccountExport } from './AccountExport';
it('downloads the authenticated ZIP with CSRF and displays export errors', async () => {
  let attempt = 0;
  const headers: string[] = [];
  server.use(
    http.get('*/api/auth/csrf', () =>
      HttpResponse.json({ headerName: 'X-CSRF-TOKEN', token: 'test' }),
    ),
    http.post('*/api/account/export', ({ request }) => {
      headers.push(request.headers.get('X-CSRF-TOKEN') ?? '');
      return attempt++ === 0
        ? HttpResponse.json(
            { detail: 'Archive unavailable. Retry.' },
            { status: 409 },
          )
        : new HttpResponse(new Uint8Array([80, 75]), {
            headers: { 'Content-Type': 'application/zip' },
          });
    }),
  );
  Object.defineProperty(URL, 'createObjectURL', {
    configurable: true,
    value: vi.fn(() => 'blob:archive'),
  });
  Object.defineProperty(URL, 'revokeObjectURL', {
    configurable: true,
    value: vi.fn(),
  });
  const click = vi
    .spyOn(HTMLAnchorElement.prototype, 'click')
    .mockImplementation(() => {});
  renderWithProviders(<AccountExport />);
  const user = userEvent.setup();
  await user.click(
    screen.getByRole('button', { name: 'Download account ZIP' }),
  );
  await screen.findByText('Archive unavailable. Retry.');
  await user.click(
    screen.getByRole('button', { name: 'Download account ZIP' }),
  );
  await screen.findByText(/Your account archive is ready/);
  expect(headers).toEqual(['test', 'test']);
  expect(click).toHaveBeenCalledOnce();
  click.mockRestore();
});
