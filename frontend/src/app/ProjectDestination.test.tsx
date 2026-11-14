import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import { api } from '../api/client';
import { Workspace } from './Workspace';

vi.mock('../api/client', () => ({ api: vi.fn() }));
const projects = [
  { id: 1, name: 'Alpha', ownerEmail: 'qa@example.com' },
  { id: 2, name: 'Beta', ownerEmail: 'qa@example.com' },
  { id: 3, name: 'Read only', ownerEmail: 'other@example.com' },
  { id: 4, name: 'Archived', ownerEmail: 'qa@example.com', archivedAt: '2026-10-22' },
];
let writes: { path: string; body: Record<string, any> }[];
let stories: any[], sprints: any[], tasks: any[];
beforeEach(() => {
  localStorage.clear(); writes = [];
  stories = [1, 2].map(id => ({ id: id * 10 + 1, projectId: id, title: `Story ${id}`, storyPoints: 1, priorityId: 2, acceptanceCriteria: 'Ready', sprintId: id * 10 + 2 }));
  sprints = [1, 2].map(id => ({ id: id * 10 + 2, projectId: id, name: `Sprint ${id}`, status: 'Planned', totalStoryPoints: 0 }));
  tasks = [];
  vi.mocked(api).mockReset();
  vi.mocked(api).mockImplementation(async (path, request) => {
    if (request?.method === 'POST') {
      const body = JSON.parse(String(request.body));
      writes.push({ path, body });
      const saved = { ...body, id: 100, version: 0 };
      if (path === '/stories') stories.push(saved);
      if (path === '/sprints') sprints.push(saved);
      if (path === '/tasks') tasks.push(saved);
      return saved;
    }
    if (path.endsWith('/role')) return { role: path.includes('/3/') ? 'Viewer' : 'Owner' };
    if (path.endsWith('/members')) return [{ id: 1, email: path.includes('/2/') ? 'beta@example.com' : 'alpha@example.com', role: 'Member' }];
    if (path.startsWith('/stories/project/')) return stories.filter(item => item.projectId === Number(path.split('/').at(-1)));
    if (path.startsWith('/sprints?')) return sprints.filter(item => item.projectId === Number(path.split('=').at(-1)));
    if (path.startsWith('/tasks?')) return tasks.filter(item => item.projectId === Number(path.split('=').at(-1)));
    if (path.endsWith('/metrics')) return { taskCount: 0, sprintHealth: { status: 'Healthy' } };
    if (path === '/auth/preferences') return { savedFilters: '[]', onboardingCompleted: true };
    return [];
  });
});
async function open(view: string, action: string) {
  render(<Workspace session={{ user: { id: 1, email: 'qa@example.com' }, projects }}
    onProjectsChanged={vi.fn()} onLogout={vi.fn()} onUserChanged={vi.fn()} onAccountDeleted={vi.fn()} />);
  fireEvent.click(within(screen.getByRole('navigation', { name: 'Main navigation' })).getByRole('button', { name: view }));
  const button = await screen.findByRole('button', { name: new RegExp(action) });
  await waitFor(() => expect(button).toBeEnabled()); fireEvent.click(button);
  return within(screen.getByRole('dialog'));
}
it('keeps a story draft, replaces sprint choices and saves in the project selected inside the form', async () => {
  const form = await open('Backlog', 'New story');
  fireEvent.change(form.getByLabelText('Title'), { target: { value: 'Destination story' } });
  fireEvent.change(form.getByLabelText('Acceptance criteria'), { target: { value: 'Preserved criteria' } });
  fireEvent.change(form.getByLabelText('Sprint'), { target: { value: '12' } });
  expect(form.queryByRole('option', { name: 'Archived' })).not.toBeInTheDocument();
  fireEvent.change(form.getByLabelText('Project'), { target: { value: '2' } });
  expect(form.getByLabelText('Title')).toHaveValue('Destination story');
  expect(form.getByLabelText('Acceptance criteria')).toHaveValue('Preserved criteria');
  expect(form.getByLabelText('Sprint')).toHaveValue('');
  await form.findByRole('option', { name: 'Sprint 2' });
  expect(form.queryByRole('option', { name: 'Sprint 1' })).not.toBeInTheDocument();
  fireEvent.change(form.getByLabelText('Sprint'), { target: { value: '22' } });
  const submit = form.getByRole('button', { name: 'Create story' });
  await waitFor(() => expect(submit).toBeEnabled()); fireEvent.click(submit);
  expect(await screen.findByRole('heading', { name: 'Destination story' })).toBeInTheDocument();
  expect(writes).toEqual([{ path: '/stories', body: expect.objectContaining({ projectId: 2, sprintId: 22 }) }]);
  expect(screen.getByRole('combobox', { name: 'Project' })).toHaveValue('2');
});
it('keeps sprint fields and creates the sprint in the selected destination', async () => {
  const form = await open('Sprints', 'New sprint');
  fireEvent.change(form.getByLabelText('Name'), { target: { value: 'Destination sprint' } });
  fireEvent.change(form.getByLabelText('Start date'), { target: { value: '2026-10-22' } });
  fireEvent.change(form.getByLabelText('End date'), { target: { value: '2026-10-30' } });
  fireEvent.change(form.getByLabelText('Project'), { target: { value: '2' } });
  expect(form.getByLabelText('Name')).toHaveValue('Destination sprint');
  const submit = form.getByRole('button', { name: 'Create sprint' });
  await waitFor(() => expect(submit).toBeEnabled()); fireEvent.click(submit);
  expect(await screen.findByRole('heading', { name: 'Destination sprint' })).toBeInTheDocument();
  expect(writes[0]).toMatchObject({ path: '/sprints', body: { projectId: 2 } });
});
it('clears task links from the old project and reloads destination stories, sprints and members', async () => {
  const form = await open('Tasks', 'New task');
  fireEvent.change(form.getByLabelText('Title'), { target: { value: 'Destination task' } });
  fireEvent.change(form.getByLabelText('User story'), { target: { value: '11' } });
  fireEvent.change(form.getByLabelText('Assignee'), { target: { value: 'alpha@example.com' } });
  fireEvent.change(form.getByLabelText('Project'), { target: { value: '2' } });
  expect(form.getByLabelText('User story')).toHaveValue('');
  expect(form.getByLabelText('Sprint')).toHaveValue('');
  expect(form.getByLabelText('Assignee')).toHaveValue('');
  await form.findByRole('option', { name: 'Story 2' });
  await form.findByRole('option', { name: 'beta@example.com' });
  expect(form.queryByRole('option', { name: 'Story 1' })).not.toBeInTheDocument();
  fireEvent.change(form.getByLabelText('User story'), { target: { value: '21' } });
  fireEvent.change(form.getByLabelText('Assignee'), { target: { value: 'beta@example.com' } });
  const submit = form.getByRole('button', { name: 'Create task' });
  await waitFor(() => expect(submit).toBeEnabled()); fireEvent.click(submit);
  expect(await screen.findByText('Destination task', { selector: 'strong' })).toBeInTheDocument();
  expect(writes[0]).toMatchObject({ path: '/tasks', body: { projectId: 2, storyId: 21, sprintId: 22, assigneeEmail: 'beta@example.com' } });
});
it('inherits the story sprint, including an unplanned story, and permits independent standalone planning', async () => {
  stories.push({ id: 13, projectId: 1, title: 'Unplanned story', sprintId: null });
  const form = await open('Tasks', 'New task');
  const sprint = form.getByLabelText('Sprint');
  expect(sprint).toBeEnabled();
  fireEvent.change(form.getByLabelText('User story'), { target: { value: '11' } });
  expect(sprint).toHaveValue('12');
  expect(sprint).toBeDisabled();
  fireEvent.change(form.getByLabelText('User story'), { target: { value: '13' } });
  expect(sprint).toHaveValue('');
  expect(sprint).toBeDisabled();
  expect(form.getByText(/Follows the story’s sprint/)).toBeVisible();
  fireEvent.change(form.getByLabelText('User story'), { target: { value: '' } });
  expect(sprint).toBeEnabled();
  fireEvent.change(sprint, { target: { value: '12' } });
  fireEvent.change(form.getByLabelText('Title'), { target: { value: 'Standalone planned task' } });
  fireEvent.click(form.getByRole('button', { name: 'Create task' }));
  await screen.findByText('Standalone planned task', { selector: 'strong' });
  expect(writes[0]).toMatchObject({ path: '/tasks', body: { storyId: null, sprintId: 12 } });
});

it('blocks creating items in a destination where the user only has read permission', async () => {
  const form = await open('Sprints', 'New sprint');
  fireEvent.change(form.getByLabelText('Project'), { target: { value: '3' } });
  expect(await form.findByRole('alert')).toHaveTextContent('cannot create');
  expect(form.getByRole('button', { name: 'Create sprint' })).toBeDisabled();
  expect(writes).toEqual([]);
});

it.each([
  ['Overview', '/projects/2/metrics'], ['Backlog', '/stories/project/2'],
  ['Sprints', '/sprints?projectId=2'], ['Tasks', '/tasks?projectId=2'],
  ['Metrics', '/projects/2/metrics'], ['Updates', '/projects/2/activity'],
  ['Calendar', '/tasks?projectId=2'], ['Retrospective', '/sprints?projectId=2'],
])('switches the current project directly on the %s page', async (view, destinationRead) => {
  render(<Workspace session={{ user: { id: 1, email: 'qa@example.com' }, projects }}
    onProjectsChanged={vi.fn()} onLogout={vi.fn()} onUserChanged={vi.fn()} onAccountDeleted={vi.fn()} />);
  fireEvent.click(within(screen.getByRole('navigation', { name: 'Main navigation' })).getByRole('button', { name: view }));
  fireEvent.change(screen.getByRole('combobox', { name: 'Current project' }), { target: { value: '2' } });
  expect(screen.getByRole('combobox', { name: 'Project' })).toHaveValue('2');
  expect(screen.getByRole('combobox', { name: 'Current project' })).toHaveValue('2');
  await waitFor(() => expect(api).toHaveBeenCalledWith(destinationRead));
});
