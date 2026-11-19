import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { expect, it } from 'vitest';
import type { Project } from '../../api/contracts';
import { mockApi, apiError } from '../../test/api';
import { renderWithProviders } from '../../test/render';
import { CustomFieldsView } from './CustomFieldsView';

const project = { id: 7, name: 'Project', key: 'QA' } as Project;
it('creates a select definition with empty initial inputs and refetches the visible list', async () => {
  let created = false;
  mockApi((path, request) => {
    if (path === '/api/auth/csrf')
      return { token: 'test', headerName: 'X-CSRF-TOKEN' };
    if (path.endsWith('/role')) return { role: 'Owner' };
    if (request.method === 'POST') {
      expect(JSON.parse(request.body!)).toEqual({
        name: 'Stage',
        kind: 'SELECT',
        options: ['Ready', 'Waiting'],
      });
      created = true;
      return {};
    }
    return created
      ? [
          {
            id: 1,
            projectId: 7,
            name: 'Stage',
            kind: 'SELECT',
            options: ['Ready', 'Waiting'],
            version: 0,
          },
        ]
      : [];
  });
  renderWithProviders(<CustomFieldsView project={project} />, {
    language: 'EN',
  });
  await screen.findByText('No custom fields yet');
  fireEvent.click(screen.getByRole('button', { name: 'New custom field' }));
  const dialog = screen.getByRole('dialog');
  expect(
    within(dialog).getByRole('textbox', { name: 'Field name' }),
  ).toHaveValue('');
  fireEvent.change(
    within(dialog).getByRole('textbox', { name: 'Field name' }),
    { target: { value: 'Stage' } },
  );
  fireEvent.change(
    within(dialog).getByRole('combobox', { name: 'Field type' }),
    { target: { value: 'SELECT' } },
  );
  fireEvent.change(
    within(dialog).getByRole('textbox', { name: 'Options, one per line' }),
    { target: { value: 'Ready\n Waiting ' } },
  );
  fireEvent.click(within(dialog).getByRole('button', { name: 'Save field' }));
  expect(await screen.findByRole('heading', { name: 'Stage' })).toBeVisible();
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
});

it('keeps a field rename draft after a version conflict and keeps its saved type immutable', async () => {
  mockApi((path, request) =>
    path === '/api/auth/csrf'
      ? { token: 'test', headerName: 'X-CSRF-TOKEN' }
      : request.method === 'PUT'
        ? apiError(412, 'Changed')
        : path.endsWith('/role')
          ? { role: 'Admin' }
          : [
              {
                id: 8,
                projectId: 7,
                name: 'Budget',
                kind: 'NUMBER',
                options: [],
                version: 3,
              },
            ],
  );
  renderWithProviders(<CustomFieldsView project={project} />, {
    language: 'SV',
  });
  fireEvent.click(
    await screen.findByRole('button', { name: 'Redigera anpassat fält' }),
  );
  expect(screen.getByRole('combobox', { name: 'Fälttyp' })).toBeDisabled();
  const input = screen.getByRole('textbox', { name: 'Fältnamn' });
  fireEvent.change(input, { target: { value: 'Kostnad' } });
  fireEvent.click(screen.getByRole('button', { name: 'Spara fält' }));
  expect(await screen.findByRole('alert')).toHaveTextContent(
    'ditt utkast finns kvar',
  );
  expect(input).toHaveValue('Kostnad');
});

it('prevents definition writes in archived projects and for ordinary members', async () => {
  mockApi((path) => (path.endsWith('/role') ? { role: 'Member' } : []));
  renderWithProviders(
    <CustomFieldsView
      project={{ ...project, archivedAt: '2026-11-18T00:00:00Z' }}
    />,
    { language: 'EN' },
  );
  await waitFor(() =>
    expect(
      screen.getByRole('button', { name: 'New custom field' }),
    ).toBeDisabled(),
  );
  expect(await screen.findByText(/You can read the fields/)).toBeVisible();
});
