import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from './App';
import { mockSignedIn } from './appTestFixtures';
describe('App.workspace', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    vi.unstubAllGlobals();
  });
  it('keeps log out inside the profile instead of the sidebar', async () => {
    mockSignedIn([]);
    render(<App />);

    fireEvent.click(await screen.findByRole('link', { name: /Leo.*Profile/i }));

    expect(document.querySelector('.workspace-online')).toBeNull();
    expect(
      await screen.findByRole('tab', { name: 'Personal details' }),
    ).toHaveAttribute('aria-selected', 'true');
    const profilePage = screen
      .getByRole('heading', { name: 'Your profile' })
      .closest('section');
    expect(profilePage).not.toBeNull();
    expect(
      within(profilePage as HTMLElement).getByRole('button', {
        name: 'Log out',
      }),
    ).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Log out' })).toHaveLength(1);
  });
  it('shares the selected project across workspace pages and keeps the full project list', async () => {
    const requests = mockSignedIn();
    render(<App />);
    const picker = await screen.findByRole('combobox', { name: 'Project' });
    expect(picker).toHaveValue('1');
    fireEvent.change(picker, { target: { value: '2' } });
    expect(localStorage.getItem('sprintflow-active-project')).toBe('2');
    expect(screen.getAllByRole('combobox', { name: /project/i })).toHaveLength(
      1,
    );

    fireEvent.click(screen.getByRole('link', { name: 'Tasks' }));
    await waitFor(() => expect(requests).toContain('/api/tasks?projectId=2'));
    expect(screen.queryByRole('combobox', { name: 'Project' })).toBe(picker);

    fireEvent.click(screen.getByRole('link', { name: 'Backlog' }));
    await waitFor(() => expect(requests).toContain('/api/stories/project/2'));
    expect(
      screen.getByRole('combobox', { name: 'Project' }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('combobox', { name: 'Language' }),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByRole('link', { name: 'Sprints' }));
    await waitFor(() => expect(requests).toContain('/api/sprints?projectId=2'));
    fireEvent.click(screen.getByRole('link', { name: 'Metrics' }));
    await waitFor(() => expect(requests).toContain('/api/projects/2/metrics'));
    fireEvent.click(screen.getByRole('link', { name: 'Updates' }));
    await waitFor(() => expect(requests).toContain('/api/projects/2/activity'));

    fireEvent.click(screen.getByRole('link', { name: 'Projects' }));
    expect(
      await screen.findByRole('heading', { name: 'Alpha' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Beta' })).toBeInTheDocument();
  });
  it('restores a saved authorized project and replaces an unavailable saved project', async () => {
    localStorage.setItem('sprintflow-active-project', '2');
    mockSignedIn();
    const { unmount } = render(<App />);
    expect(
      await screen.findByRole('combobox', { name: 'Project' }),
    ).toHaveValue('2');
    unmount();

    localStorage.setItem('sprintflow-active-project', '999');
    mockSignedIn();
    render(<App />);
    expect(
      await screen.findByRole('combobox', { name: 'Project' }),
    ).toHaveValue('1');
    await waitFor(() =>
      expect(localStorage.getItem('sprintflow-active-project')).toBe('1'),
    );
  });
  it('shows selected-project metrics on Overview and handles an empty project list', async () => {
    const requests = mockSignedIn();
    const { unmount } = render(<App />);
    expect(
      await screen.findByRole('heading', { name: 'Alpha' }),
    ).toBeInTheDocument();
    expect(await screen.findByText('8')).toBeInTheDocument();
    expect(requests).toContain('/api/projects/1/metrics');

    unmount();
    mockSignedIn([]);
    render(<App />);
    expect(
      await screen.findByRole('combobox', { name: 'Project' }),
    ).toBeDisabled();
    expect(
      await screen.findByRole('heading', { name: 'Choose a project' }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'Create project' }),
    ).toBeInTheDocument();
  });
});
