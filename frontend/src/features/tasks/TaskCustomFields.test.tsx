import { act, fireEvent, screen, waitFor } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import type { CustomFieldDefinition, Task } from '../../api/contracts';
import { taskCustomFieldsQuery } from '../../api/customFields';
import { mockApi, apiError } from '../../test/api';
import { renderWithProviders } from '../../test/render';
import { TaskCustomFieldsDrawer } from './TaskCustomFields';

const task = {
  id: 1,
  projectId: 7,
  key: 'QA-1',
  title: 'Example',
  version: 4,
} as Task;
const fields: CustomFieldDefinition[] = [
  {
    id: 8,
    projectId: 7,
    name: 'Budget',
    kind: 'NUMBER',
    options: [],
    version: 0,
  },
];

it('sends exact decimal strings and preserves a dirty draft during realtime refetch and stale writes', async () => {
  let version = 4;
  const writes: unknown[] = [];
  mockApi((path, request) => {
    if (path === '/api/auth/csrf')
      return { token: 'test', headerName: 'X-CSRF-TOKEN' };
    if (path === '/api/tasks/1/custom-fields' && request.method === 'PUT') {
      writes.push(JSON.parse(request.body!));
      return apiError(412, 'Changed');
    }
    return {
      taskId: 1,
      version,
      values: [{ fieldId: 8, value: '1', displayValue: '1' }],
    };
  });
  const saved = vi.fn();
  const { client } = renderWithProviders(
    <TaskCustomFieldsDrawer
      task={task}
      definitions={fields}
      members={[]}
      readOnly={false}
      onClose={vi.fn()}
      onSaved={saved}
    />,
    { language: 'EN' },
  );
  const input = await screen.findByRole('textbox', { name: 'Budget' });
  fireEvent.change(input, { target: { value: '9007199254740993.123456' } });
  version = 5;
  await act(async () => {
    await client.invalidateQueries({
      queryKey: taskCustomFieldsQuery(7, 1).queryKey,
    });
  });
  expect(input).toHaveValue('9007199254740993.123456');
  await waitFor(() =>
    expect(screen.getByText(/The task changed since/)).toBeVisible(),
  );
  fireEvent.click(screen.getByRole('button', { name: 'Save values' }));
  expect(await screen.findByRole('alert')).toHaveTextContent(
    'Your draft is kept',
  );
  expect(writes).toEqual([
    { values: [{ fieldId: 8, value: '9007199254740993.123456' }] },
  ]);
  expect(saved).not.toHaveBeenCalled();
  expect(input).toHaveValue('9007199254740993.123456');
});

it('shows project member labels to a Swedish reader without editable controls', async () => {
  mockApi(() => ({
    taskId: 1,
    version: 4,
    values: [{ fieldId: 8, value: '12', displayValue: 'member@example.test' }],
  }));
  renderWithProviders(
    <TaskCustomFieldsDrawer
      task={task}
      definitions={[{ ...fields[0]!, kind: 'USER', name: 'Granskare' }]}
      members={[]}
      readOnly
      onClose={vi.fn()}
      onSaved={vi.fn()}
    />,
    { language: 'SV' },
  );
  expect(await screen.findByText('member@example.test')).toBeVisible();
  expect(
    screen.queryByRole('button', { name: 'Spara värden' }),
  ).not.toBeInTheDocument();
  await waitFor(() =>
    expect(screen.getByText(/Du kan läsa värdena/)).toBeVisible(),
  );
});
