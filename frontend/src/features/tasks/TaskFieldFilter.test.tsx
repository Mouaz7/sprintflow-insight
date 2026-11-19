import { projectScope } from '../../api/queries';
import { act, fireEvent, screen, waitFor } from '@testing-library/react';
import { expect, it } from 'vitest';
import { apiError, mockApi } from '../../test/api';
import { renderWithProviders } from '../../test/render';
import { TasksView } from './TasksView';

it('filters through server matches, hides unfiltered cards for incomplete criteria, and exports identical exact criteria', async () => {
  const requests: string[] = [];
  let unavailable = false;
  mockApi((path) => {
    requests.push(path);
    if (path.endsWith('/role')) return { role: 'Owner' };
    if (path.endsWith('/custom-fields'))
      return unavailable
        ? []
        : [
            {
              id: 8,
              projectId: 7,
              name: 'Budget',
              kind: 'NUMBER',
              options: [],
              version: 0,
            },
          ];
    if (path.includes('/custom-fields/matches?'))
      return unavailable
        ? apiError(404, 'Custom field not found in this project.')
        : { taskIds: [1] };
    if (path.startsWith('/api/tasks?'))
      return [1, 2].map((id) => ({
        id,
        projectId: 7,
        key: `QA-${id}`,
        title: `Task ${id}`,
        status: 'To Do',
        storyPoints: 1,
        version: 0,
      }));
    if (path.endsWith('/dependencies'))
      return { dependencies: [], blockedTaskIds: [], externalBlocks: [] };
    if (path.endsWith('/wip-limits')) return { version: 0, limits: [] };
    return [];
  });
  const { client } = renderWithProviders(
    <TasksView hasProjects projectId={7} />,
    {
      language: 'EN',
    },
  );
  await screen.findByText('Task 2');
  fireEvent.change(
    await screen.findByRole('combobox', { name: /^Custom field$/ }),
    { target: { value: '8' } },
  );
  await waitFor(() =>
    expect(screen.queryByText('Task 2')).not.toBeInTheDocument(),
  );
  fireEvent.change(screen.getByRole('combobox', { name: 'Field condition' }), {
    target: { value: 'GREATER_THAN' },
  });
  expect(screen.queryByText('Task 1')).not.toBeInTheDocument();
  fireEvent.change(screen.getByRole('textbox', { name: 'Filter value' }), {
    target: { value: '9007199254740993.1' },
  });
  expect(await screen.findByText('Task 1')).toBeVisible();
  expect(screen.queryByText('Task 2')).not.toBeInTheDocument();
  const url = new URL(
    screen.getByRole('link', { name: /CSV/ }).getAttribute('href')!,
    'http://localhost',
  );
  expect(url.searchParams.get('customValue')).toBe('9007199254740993.1');
  expect(
    requests.some((path) => path.includes('value=9007199254740993.1')),
  ).toBe(true);
  unavailable = true;
  await act(async () => {
    await client.invalidateQueries({ queryKey: projectScope(7) });
  });
  await screen.findByRole('alert');
  expect(screen.queryByText('Task 1')).not.toBeInTheDocument();
  expect(screen.getByRole('link', { name: /CSV/ })).toHaveAttribute(
    'aria-disabled',
    'true',
  );
  fireEvent.click(screen.getByRole('button', { name: 'Clear field filter' }));
  expect(screen.getByText('Task 2')).toBeVisible();
  expect(screen.getByRole('link', { name: /CSV/ })).not.toHaveAttribute(
    'href',
    expect.stringContaining('customFieldId'),
  );
});
