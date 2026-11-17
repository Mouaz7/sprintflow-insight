import { fireEvent, screen, waitFor, within } from '@testing-library/react';
// Feature components need the query client and language providers.
import { renderWithProviders as render } from '../test/render';
import { useState } from 'react';
import { beforeEach, expect, it, vi } from 'vitest';
import { api } from '../api/client';
import { ProjectsView } from '../features/projects/ProjectsView';
import { TasksView } from '../features/tasks/TasksView';
import { ProjectInsightView } from './ProjectInsights';

vi.mock('../api/client', () => ({ api: vi.fn() }));
beforeEach(() => {
  vi.mocked(api).mockReset();
});

it.each(['backlog', 'sprints'] as const)(
  'shows a saved %s item without depending on a second list request',
  async (view) => {
    const listPath =
      view === 'backlog' ? '/stories/project/2' : '/sprints?projectId=2';
    let listReads = 0;
    vi.mocked(api).mockImplementation(async (path, request) => {
      if (request?.method === 'POST')
        return { ...JSON.parse(String(request.body)), id: 21, version: 0 };
      if (path === listPath && ++listReads > 1)
        throw new Error('The list could not refresh.');
      return [];
    });
    render(<ProjectInsightView view={view} projectId={2} hasProjects />);
    fireEvent.click(
      await screen.findByRole('button', {
        name: view === 'backlog' ? /New story/ : /New sprint/,
      }),
    );
    fireEvent.change(
      screen.getByLabelText(view === 'backlog' ? 'Title' : 'Name', {
        exact: true,
      }),
      { target: { value: 'Saved in Beta' } },
    );
    if (view === 'backlog')
      fireEvent.change(screen.getByLabelText('Acceptance criteria'), {
        target: { value: 'Visible immediately' },
      });
    else {
      fireEvent.change(screen.getByLabelText('Start date'), {
        target: { value: '2026-10-22' },
      });
      fireEvent.change(screen.getByLabelText('End date'), {
        target: { value: '2026-10-30' },
      });
    }
    const submit = within(screen.getByRole('dialog')).getByRole('button', {
      name: view === 'backlog' ? 'Create story' : 'Create sprint',
    });
    await waitFor(() => expect(submit).toBeEnabled());
    fireEvent.click(submit);
    expect(
      await screen.findByRole('heading', { name: 'Saved in Beta' }),
    ).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(api).toHaveBeenCalledWith(
      view === 'backlog' ? '/stories' : '/sprints',
      expect.objectContaining({
        method: 'POST',
        body: expect.stringContaining('"projectId":2'),
      }),
    );
  },
);

it('reveals a created task when search and a saved blocked filter would hide it', async () => {
  vi.mocked(api).mockImplementation(async (path, request) => {
    if (request?.method === 'POST')
      return { ...JSON.parse(String(request.body)), id: 22, version: 0 };
    if (path.endsWith('/role')) return { role: 'Owner' };
    if (path === '/auth/preferences') return { savedFilters: '[]' };
    return [];
  });
  render(<TasksView hasProjects projectId={2} />);
  await waitFor(() =>
    expect(screen.getByRole('button', { name: /New task/ })).toBeEnabled(),
  );
  fireEvent.change(screen.getByLabelText('Saved filters'), {
    target: { value: 'blocked' },
  });
  fireEvent.change(screen.getByLabelText('Search tasks'), {
    target: { value: 'unrelated search' },
  });
  fireEvent.click(screen.getByRole('button', { name: /New task/ }));
  fireEvent.change(screen.getByLabelText('Title', { exact: true }), {
    target: { value: 'Visible new task' },
  });
  fireEvent.click(screen.getByRole('button', { name: 'Create task' }));
  expect(
    await screen.findByText('Visible new task', { selector: 'strong' }),
  ).toBeInTheDocument();
  expect(screen.getByLabelText('Search tasks')).toHaveValue('');
  expect(screen.getByLabelText('Saved filters')).toHaveValue('');
  expect(
    screen.getByLabelText('Manually blocked', { exact: true }),
  ).not.toBeChecked();
});

it('reveals and selects a new project despite search and archived-project filters', async () => {
  const alpha = { id: 1, name: 'Alpha', ownerEmail: 'qa@example.com' };
  const beta = { id: 2, name: 'Beta', ownerEmail: 'qa@example.com' };
  const select = vi.fn();
  vi.mocked(api).mockResolvedValue(beta);
  function Harness() {
    const [projects, setProjects] = useState([alpha]);
    return (
      <ProjectsView
        projects={projects}
        user={{ id: 1, email: 'qa@example.com' }}
        onSelect={select}
        onChanged={async () => {
          setProjects([alpha, beta]);
        }}
      />
    );
  }
  render(<Harness />);
  fireEvent.change(screen.getByLabelText('Search projects'), {
    target: { value: 'Alpha' },
  });
  fireEvent.click(screen.getByRole('button', { name: 'Archived projects' }));
  fireEvent.click(screen.getByRole('button', { name: /Create project/ }));
  fireEvent.change(screen.getByLabelText('Project name'), {
    target: { value: 'Beta' },
  });
  fireEvent.click(
    within(screen.getByRole('dialog')).getByRole('button', {
      name: 'Create project',
    }),
  );
  expect(
    await screen.findByRole('heading', { name: 'Beta' }),
  ).toBeInTheDocument();
  expect(screen.getByLabelText('Search projects')).toHaveValue('');
  expect(
    screen.getByRole('button', { name: 'Active projects' }),
  ).toHaveAttribute('aria-pressed', 'true');
  expect(select).toHaveBeenCalledExactlyOnceWith(2);
});
