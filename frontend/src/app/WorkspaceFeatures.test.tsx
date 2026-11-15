import { fireEvent, screen, waitFor, within } from '@testing-library/react';
// Feature components need the query client and language providers.
import { renderWithProviders as render } from '../test/render';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '../api/client';
import type { Preferences, Task } from '../api/contracts';
import {
  PublicReportView,
  ShareReportCard,
} from '../features/insights/SharedReports';
import { Onboarding } from '../features/onboarding/Onboarding';
import { CalendarView } from '../features/planning/CalendarView';
import { RetrospectiveView } from '../features/planning/RetrospectiveView';
import { PreferencesPanel } from '../features/profile/PreferencesPanel';
import { TrashView } from '../features/projects/TrashView';
import { SavedFilters } from '../features/tasks/SavedFilters';
import {
  TaskDiscussion,
  TaskMetadata,
} from '../features/tasks/TaskCollaboration';
import { NotificationBell } from './NotificationBell';
vi.mock('../api/client', () => ({ api: vi.fn() }));
const task: Task = {
  id: 1,
  title: 'Feature QA',
  projectId: 1,
  status: 'To Do',
  storyPoints: 3,
  labels: [],
  checklist: [],
};
const preferences: Preferences = {
  language: 'EN',
  theme: 'light',
  notifications: true,
  onboardingCompleted: false,
  savedFilters: '[]',
};
describe('Workspace feature components', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.mocked(api).mockReset().mockResolvedValue([]);
    localStorage.clear();
  });
  it('preserves a failed checklist/label draft and sends required checklist steps', async () => {
    const onChange = vi.fn().mockResolvedValue(false);
    render(<TaskMetadata task={task} onChange={onChange} />);
    fireEvent.click(screen.getByText('Labels and checklist'));
    fireEvent.change(screen.getByLabelText('New label'), {
      target: { value: 'Bug' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Add label' }));
    await waitFor(() =>
      expect(onChange).toHaveBeenCalledWith({ labels: ['Bug'] }),
    );
    expect(screen.getByLabelText('New label')).toHaveValue('Bug');
    onChange.mockResolvedValue(true);
    fireEvent.change(screen.getByLabelText('New step'), {
      target: { value: 'Review accessibility' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Add step' }));
    await waitFor(() =>
      expect(onChange).toHaveBeenCalledWith({
        checklist: [
          expect.objectContaining({
            label: 'Review accessibility',
            required: true,
            completed: false,
          }),
        ],
      }),
    );
    await waitFor(() =>
      expect(screen.getByLabelText('New step')).toHaveValue(''),
    );
  });
  it('retains a failed comment, then persists it and shows moderation controls', async () => {
    vi.mocked(api)
      .mockResolvedValueOnce([])
      .mockRejectedValueOnce(new Error('Connection unavailable'))
      .mockResolvedValueOnce({
        id: 9,
        message: 'Keep this draft',
        authorEmail: 'qa@example.com',
        createdAt: '2026-10-12T00:00:00Z',
      });
    render(
      <TaskDiscussion
        task={task}
        userEmail="qa@example.com"
        canModerate={false}
        readOnly={false}
        onClose={vi.fn()}
      />,
    );
    await screen.findByText('Start the discussion on this task.');
    fireEvent.change(screen.getByLabelText('New comment'), {
      target: { value: 'Keep this draft' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Post comment' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Connection unavailable',
    );
    expect(screen.getByLabelText('New comment')).toHaveValue('Keep this draft');
    fireEvent.click(screen.getByRole('button', { name: 'Post comment' }));
    expect(await screen.findByText('Keep this draft')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Edit' })).toBeInTheDocument();
    expect(screen.getByLabelText('New comment')).toHaveValue('');
  });
  it('keeps comment creation unavailable for viewers', async () => {
    render(
      <TaskDiscussion
        task={task}
        userEmail="viewer@example.com"
        canModerate={false}
        readOnly
        onClose={vi.fn()}
      />,
      { language: 'SV' },
    );
    await screen.findByText('Starta diskussionen om den här uppgiften.');
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });
  it('marks notifications read and restores bell focus on Escape', async () => {
    vi.mocked(api).mockImplementation(async (path) =>
      path === '/notifications'
        ? [
            {
              id: 4,
              projectId: 1,
              message: 'Assigned QA',
              read: false,
              createdAt: '2026-10-12T00:00:00Z',
            },
          ]
        : undefined,
    );
    render(<NotificationBell onProject={vi.fn()} />, { language: 'EN' });
    const bell = await screen.findByRole('button', {
      name: 'Notifications, 1 unread',
    });
    fireEvent.click(bell);
    fireEvent.click(screen.getByRole('button', { name: 'Mark all read' }));
    await waitFor(() =>
      expect(api).toHaveBeenCalledWith('/notifications/read', {
        method: 'PUT',
      }),
    );
    await screen.findByRole('button', { name: 'Notifications' });
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(
      screen.queryByRole('region', { name: 'Notification list' }),
    ).not.toBeInTheDocument();
    expect(bell).toHaveFocus();
  });
  it.each([
    {
      language: 'EN' as const,
      count: 2,
      badge: '2',
      name: 'Notifications, 2 unread',
    },
    {
      language: 'SV' as const,
      count: 2,
      badge: '2',
      name: 'Notiser, 2 olästa',
    },
    {
      language: 'EN' as const,
      count: 120,
      badge: '99+',
      name: 'Notifications, 99+ unread (120 total)',
    },
    {
      language: 'SV' as const,
      count: 120,
      badge: '99+',
      name: 'Notiser, 99+ olästa (120 totalt)',
    },
  ])(
    'includes the visible unread badge in the $language accessible name for $count notifications',
    async ({ language, count, badge, name }) => {
      vi.mocked(api).mockResolvedValue(
        Array.from({ length: count }, (_, id) => ({
          id,
          projectId: 1,
          message: 'Assigned QA',
          read: false,
          createdAt: '2026-10-12T00:00:00Z',
        })),
      );
      render(<NotificationBell onProject={vi.fn()} />, { language });
      const bell = await screen.findByRole('button', { name });
      expect(bell).toHaveTextContent(badge);
    },
  );
  it('saves project-scoped filters and retains names after a rejected save', async () => {
    vi.mocked(api)
      .mockResolvedValueOnce(preferences)
      .mockRejectedValueOnce(new Error('Could not save'));
    const onApply = vi.fn();
    render(
      <SavedFilters
        projectId={1}
        userEmail="qa@example.com"
        current={{
          query: 'QA',
          status: 'Review',
          sprint: '',
          assignee: '',
          due: '',
          blocked: false,
          label: 'Bug',
        }}
        onApply={onApply}
      />,
    );
    await waitFor(() => expect(api).toHaveBeenCalledWith('/auth/preferences'));
    fireEvent.change(screen.getByLabelText('Saved filters'), {
      target: { value: 'mine' },
    });
    expect(onApply).toHaveBeenCalledWith(
      expect.objectContaining({ assignee: 'qa@example.com' }),
    );
    fireEvent.click(screen.getByRole('button', { name: 'Save filter' }));
    fireEvent.change(screen.getByLabelText('Filter name'), {
      target: { value: 'Review bugs' },
    });
    fireEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', { name: 'Save' }),
    );
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Could not save',
    );
    expect(screen.getByLabelText('Filter name')).toHaveValue('Review bugs');
    vi.mocked(api).mockResolvedValue(preferences);
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() =>
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument(),
    );
    const request = vi.mocked(api).mock.calls.at(-1)!;
    expect(
      JSON.parse(JSON.parse(String(request[1]?.body)).savedFilters)[0],
    ).toMatchObject({
      projectId: 1,
      name: 'Review bugs',
      criteria: { query: 'QA', label: 'Bug' },
    });
  });
  it('restores deleted work before refreshing the workspace', async () => {
    vi.mocked(api)
      .mockResolvedValueOnce([
        {
          kind: 'task',
          id: 8,
          projectId: 1,
          title: 'Deleted QA',
          version: 2,
          trashedAt: '2026-10-12T00:00:00Z',
        },
      ])
      .mockResolvedValueOnce(undefined)
      .mockResolvedValueOnce([]);
    const onChanged = vi.fn().mockResolvedValue(undefined);
    render(
      <TrashView
        projects={[]}
        userEmail="qa@example.com"
        onChanged={onChanged}
      />,
      { language: 'EN' },
    );
    await screen.findByRole('heading', { name: 'Deleted QA' });
    fireEvent.click(screen.getByRole('button', { name: 'Restore' }));
    await screen.findByRole('heading', { name: 'Your recycle bin is empty' });
    expect(api).toHaveBeenCalledWith('/workspace/trash/task/8', {
      method: 'PUT',
      headers: { 'If-Match': '"2"' },
      body: JSON.stringify({ restore: true }),
    });
    expect(onChanged).toHaveBeenCalledTimes(1);
  });
  it('saves appearance preferences and notifies the navigation', async () => {
    vi.mocked(api).mockImplementation(async (path, init) =>
      path === '/auth/integrations'
        ? { gmailReady: false, googleReady: false }
        : init?.method === 'PUT'
          ? {
              ...preferences,
              language: 'SV',
              theme: 'dark',
              notifications: false,
            }
          : preferences,
    );
    const onAppearance = vi.fn();
    render(<PreferencesPanel onAppearance={onAppearance} />, {
      language: 'EN',
    });
    await waitFor(() =>
      expect(
        screen.getByRole('button', { name: 'Save settings' }),
      ).toBeEnabled(),
    );
    fireEvent.change(screen.getByLabelText('Language'), {
      target: { value: 'SV' },
    });
    fireEvent.change(screen.getByLabelText('Theme'), {
      target: { value: 'dark' },
    });
    fireEvent.click(screen.getByRole('checkbox'));
    fireEvent.click(screen.getByRole('button', { name: 'Save settings' }));
    await waitFor(() =>
      expect(onAppearance).toHaveBeenCalledWith('SV', 'dark'),
    );
    expect(localStorage.getItem('sprintflow-theme')).toBe('dark');
  });
  it('shows a calendar empty state without requesting another project', () => {
    render(<CalendarView />);
    expect(
      screen.getByText('Choose or create a project first.'),
    ).toBeInTheDocument();
    expect(api).not.toHaveBeenCalled();
  });
  it('preserves retrospective notes after failed writes', async () => {
    vi.mocked(api).mockImplementation(async (path, init) => {
      if (init?.method === 'POST') throw new Error('Retry later');
      if (path.includes('/role')) return { role: 'Owner' };
      if (path.startsWith('/sprints'))
        return [{ id: 1, name: 'QA Sprint', status: 'Completed' }];
      return [];
    });
    render(<RetrospectiveView projectId={1} userEmail="qa@example.com" />);
    fireEvent.click(await screen.findByRole('button', { name: 'New note' }));
    fireEvent.change(screen.getByLabelText('Note'), {
      target: { value: 'Keep learning' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Save note' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Retry later');
    expect(screen.getByLabelText('Note')).toHaveValue('Keep learning');
  });
  it('requires confirmation before creating a public report link', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    vi.mocked(api).mockImplementation(async (path) =>
      path.includes('/role') ? { role: 'Owner' } : [],
    );
    render(<ShareReportCard projectId={1} />);
    fireEvent.click(
      await screen.findByRole('button', { name: 'Create report link' }),
    );
    await waitFor(() =>
      expect(window.confirm).toHaveBeenCalledWith(
        expect.stringContaining('Anyone with the link'),
      ),
    );
    expect(api).not.toHaveBeenCalledWith(
      '/projects/1/reports',
      expect.objectContaining({ method: 'POST' }),
    );
  });
  it('renders expired report errors and blocks scripts in report text', async () => {
    vi.mocked(api).mockResolvedValue({
      projectName: '<script>unsafe()</script>',
      description: 'Plain description',
      generatedAt: '2026-10-12T00:00:00Z',
      taskCount: 1,
      statusCounts: { Done: 1 },
    });
    render(<PublicReportView token="test-token" />);
    expect(
      await screen.findByRole('heading', { name: '<script>unsafe()</script>' }),
    ).toBeInTheDocument();
    expect(document.querySelector('script')).toBeNull();
  });
  it('persists onboarding completion before navigating', async () => {
    vi.mocked(api).mockResolvedValue(preferences);
    const onClose = vi.fn(),
      onNavigate = vi.fn();
    render(<Onboarding onClose={onClose} onNavigate={onNavigate} />, {
      language: 'EN',
    });
    fireEvent.click(screen.getByRole('button', { name: 'Open projects' }));
    await waitFor(() => expect(onNavigate).toHaveBeenCalledWith('projects'));
    expect(onClose).toHaveBeenCalledTimes(1);
    expect(api).toHaveBeenCalledWith('/auth/preferences', {
      method: 'PUT',
      body: JSON.stringify({ onboardingCompleted: true }),
    });
  });
});
