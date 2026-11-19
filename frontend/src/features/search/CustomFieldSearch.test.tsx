import { fireEvent, screen, waitFor } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { mockApi } from '../../test/api';
import { renderWithProviders } from '../../test/render';
import { CommandCenter } from './CommandCenter';

it('scopes field search to the project and prevents a task-key jump from bypassing the condition', async () => {
  const calls: string[] = [];
  mockApi((path) => {
    calls.push(path);
    if (path.endsWith('/custom-fields'))
      return [
        {
          id: 8,
          projectId: 7,
          name: 'Budget',
          kind: 'NUMBER',
          options: [],
          version: 0,
        },
      ];
    if (path.endsWith('/members')) return [];
    return { items: [], nextCursor: null };
  });
  const navigate = vi.fn();
  renderWithProviders(
    <CommandCenter
      projectId={7}
      canCreateTask
      onNavigate={navigate}
      onNewTask={vi.fn()}
    />,
    { language: 'EN' },
  );
  fireEvent.click(screen.getByRole('button', { name: 'Search' }));
  const input = await screen.findByRole('combobox', {
    name: 'Search or run a command',
  });
  const filter = await screen.findByRole('combobox', {
    name: /^Custom field$/,
  });
  await screen.findByRole('option', { name: 'Budget' });
  fireEvent.change(filter, { target: { value: '8' } });
  fireEvent.change(input, { target: { value: 'QA-1' } });
  fireEvent.keyDown(input, { key: 'Enter' });
  await waitFor(() =>
    expect(
      calls.some(
        (path) => path.includes('/search?') && path.includes('customFieldId=8'),
      ),
    ).toBe(true),
  );
  expect(calls.find((path) => path.includes('/search?'))).toContain(
    'projectId=7',
  );
  expect(calls.some((path) => path.includes('/tasks/by-key/'))).toBe(false);
  expect(navigate).not.toHaveBeenCalled();
});
