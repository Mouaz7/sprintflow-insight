import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect } from 'vitest';
import { http, HttpResponse } from 'msw';
import { mockApi } from '../../test/api';
import { server } from '../../test/server';
import { renderWithProviders } from '../../test/render';
import { expectNoSeriousA11yViolations } from '../../test/axe';
import { ConfirmProvider } from '../../shared/ui/ui';
import { DataView } from './DataView';
import type { ImportRequest } from './contracts';
const project = { id: 1, name: 'Import project', ownerEmail: 'owner@test.se' };
const preview = {
  columns: ['title', 'status'],
  mapping: { title: 'title', status: 'status' },
  rows: [
    {
      rowNumber: 1,
      title: 'Imported task',
      description: '',
      status: 'To Do',
      sourceKey: 'DEMO-1',
      storyPoints: 0,
      assigneeEmail: null,
      labels: [],
      dueAt: null,
      createdAt: null,
      startedAt: null,
      completedAt: null,
      customValues: {},
      errors: [],
      warnings: [],
      duplicate: false,
    },
  ],
  fingerprint: 'a'.repeat(64),
  createCount: 1,
  skipCount: 0,
  canCommit: true,
  previousBatchId: null,
};
function setup(role = 'Owner', archived = false, language: 'EN' | 'SV' = 'EN') {
  const writes: ImportRequest[] = [];
  mockApi((path, request) => {
    if (path === '/api/auth/csrf')
      return { headerName: 'X-CSRF-TOKEN', token: 'test' };
    if (path.endsWith('/role')) return { role };
    if (path.endsWith('/custom-fields')) return [];
    if (path.includes('/audit')) return { items: [], nextBefore: null };
    if (path.endsWith('/imports/preview')) {
      writes.push(JSON.parse(request.body ?? '{}') as ImportRequest);
      return preview;
    }
    if (path.endsWith('/imports') && request.method === 'POST') {
      writes.push(JSON.parse(request.body ?? '{}') as ImportRequest);
      return {
        batchId: 'batch-1',
        createdCount: 1,
        skippedCount: 0,
        replayed: false,
      };
    }
    if (path.endsWith('/imports')) return [];
    return {};
  });
  const view = renderWithProviders(
    <ConfirmProvider>
      <DataView
        project={{
          ...project,
          ...(archived ? { archivedAt: '2026-01-01T00:00:00Z' } : {}),
        }}
      />
    </ConfirmProvider>,
    { language },
  );
  return { ...view, writes };
}
async function upload() {
  const user = userEvent.setup();
  await waitFor(() =>
    expect(screen.getByLabelText('Source file')).toBeEnabled(),
  );
  const file = new File(['title,status\nImported task,To Do'], 'tasks.csv', {
    type: 'text/csv',
  });
  Object.defineProperty(file, 'text', {
    value: async () => 'title,status\nImported task,To Do',
  });
  await user.upload(screen.getByLabelText('Source file'), file);
  await waitFor(() =>
    expect(screen.getByRole('button', { name: 'Preview file' })).toBeEnabled(),
  );
  await user.click(screen.getByRole('button', { name: 'Preview file' }));
  await screen.findByRole('heading', { name: 'Preview rows' });
  return user;
}
describe('Import preview and audit', () => {
  it('invalidates preview after options change and sends only the reviewed fingerprint', async () => {
    const { writes, container } = setup();
    const user = await upload();
    expect(writes).toHaveLength(1);
    expect(writes[0]?.expectedFingerprint).toBeUndefined();
    await user.click(screen.getByLabelText(/Allow shortening/));
    expect(screen.getByRole('button', { name: 'Import tasks' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Preview file' }));
    await waitFor(() =>
      expect(
        screen.getByRole('button', { name: 'Import tasks' }),
      ).toBeEnabled(),
    );
    await user.click(screen.getByRole('button', { name: 'Import tasks' }));
    const dialog = await screen.findByRole('alertdialog');
    await user.click(within(dialog).getByRole('button', { name: /confirm/i }));
    await screen.findByText(/Import complete/);
    expect(writes.at(-1)).toMatchObject({
      expectedFingerprint: preview.fingerprint,
      truncateText: true,
    });
    await expectNoSeriousA11yViolations(container);
  });
  it('retains file and mapping after a conflict so preview can be retried', async () => {
    setup();
    const user = await upload();
    server.use(
      http.post('*/api/projects/1/imports', () =>
        HttpResponse.json(
          {
            detail:
              'Preview this file again after changing its mapping, members or custom fields.',
          },
          { status: 409 },
        ),
      ),
    );
    await user.click(screen.getByRole('button', { name: 'Import tasks' }));
    await user.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', {
        name: /confirm/i,
      }),
    );
    await screen.findByText(/Preview this file again/);
    expect(screen.getByLabelText('Title')).toHaveValue('title');
    expect(screen.getByRole('button', { name: 'Preview file' })).toBeEnabled();
  });
  it.each(['Viewer', 'Member'])(
    'hides admin audit and history for %s',
    async (role) => {
      const { container } = setup(role);
      await waitFor(() =>
        expect(
          screen.getByRole('button', { name: 'Preview file' }),
        ).toBeDisabled(),
      );
      expect(
        screen.queryByRole('heading', { name: 'Administrative audit log' }),
      ).not.toBeInTheDocument();
      expect(
        screen.queryByLabelText(/Preserve historical/),
      ).not.toBeInTheDocument();
      if (role === 'Viewer')
        expect(screen.getByLabelText('Source file')).toBeDisabled();
      await expectNoSeriousA11yViolations(container);
    },
  );
  it('renders a Swedish archived project with readable audit and disabled import', async () => {
    const { container } = setup('Admin', true, 'SV');
    await screen.findByRole('heading', {
      name: 'Administrativ granskningslogg',
    });
    expect(screen.getByLabelText('Källfil')).toBeDisabled();
    expect(screen.getByText('Inga importer ännu.')).toBeInTheDocument();
    await expectNoSeriousA11yViolations(container);
  });
});
