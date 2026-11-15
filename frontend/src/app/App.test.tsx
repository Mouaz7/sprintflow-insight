import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from './App';

const projects = [
  {
    id: 1,
    name: 'Alpha',
    description: 'First project',
    ownerEmail: 'leo@example.com',
  },
  {
    id: 2,
    name: 'Beta',
    description: 'Second project',
    ownerEmail: 'leo@example.com',
  },
];

function mockSignedIn(projectList = projects) {
  const requests: string[] = [];
  vi.stubGlobal(
    'fetch',
    vi.fn().mockImplementation(async (input: RequestInfo | URL) => {
      const url = String(input);
      requests.push(url);
      const payload = url.endsWith('/auth/me')
        ? { id: 1, email: 'leo@example.com', displayName: 'Leo' }
        : url.endsWith('/projects')
          ? projectList
          : url.endsWith('/metrics')
            ? {
                taskCount: 8,
                completionRate: 50,
                throughputLast14Days: 3,
                sprintHealth: { status: 'Healthy' },
              }
            : [];
      return { ok: true, status: 200, json: async () => payload };
    }),
  );
  return requests;
}

describe('App', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    vi.unstubAllGlobals();
  });

  it('keeps the current view and selected project when the workspace is reloaded', async () => {
    mockSignedIn();
    const first = render(<App />);
    const navigation = await screen.findByRole('navigation', { name: 'Main navigation' });
    fireEvent.change(screen.getByRole('combobox', { name: 'Project' }), {
      target: { value: '2' },
    });
    fireEvent.click(within(navigation).getByRole('button', { name: 'Tasks' }));
    await screen.findByRole('heading', { name: 'Tasks' });
    first.unmount();

    render(<App />);
    await screen.findByRole('heading', { name: 'Tasks' });
    expect(screen.getByRole('combobox', { name: 'Project' })).toHaveValue('2');
    expect(within(screen.getByRole('navigation', { name: 'Main navigation' }))
      .getByRole('button', { name: 'Tasks' })).toHaveAttribute('aria-current', 'page');
  });

  it.each(['not-a-view', 'constructor'])('ignores an invalid remembered view: %s', async (saved) => {
    sessionStorage.setItem('sprintflow-view-v1:1', saved);
    mockSignedIn();
    render(<App />);
    const navigation = await screen.findByRole('navigation', { name: 'Main navigation' });
    expect(within(navigation).getByRole('button', { name: 'Overview' }))
      .toHaveAttribute('aria-current', 'page');
  });

  it('does not restore the remembered view of another account', async () => {
    sessionStorage.setItem('sprintflow-view-v1:2', 'profile');
    mockSignedIn();
    render(<App />);
    const navigation = await screen.findByRole('navigation', { name: 'Main navigation' });
    expect(within(navigation).getByRole('button', { name: 'Overview' }))
      .toHaveAttribute('aria-current', 'page');
  });

  it('shows the sign-in page when the server reports no active session', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 401,
        json: async () => ({ message: 'Sign in to continue.' }),
      }),
    );
    render(<App />);
    expect(
      await screen.findByRole('heading', { name: 'Welcome back.' }),
    ).toBeInTheDocument();
  });

  it('restores and saves the theme on sign-in, registration and password recovery', async () => {
    localStorage.setItem('sprintflow-theme', 'dark');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 401,
        json: async () => ({ message: 'Sign in to continue.' }),
      }),
    );
    render(<App />);
    await screen.findByRole('heading', { name: 'Welcome back.' });
    expect(document.querySelector('.auth-page')).toHaveAttribute(
      'data-theme',
      'dark',
    );
    fireEvent.click(
      screen.getByRole('button', { name: 'Switch to light mode' }),
    );
    expect(document.querySelector('.auth-page')).toHaveAttribute(
      'data-theme',
      'light',
    );
    expect(localStorage.getItem('sprintflow-theme')).toBe('light');
    fireEvent.click(screen.getByRole('button', { name: 'Create account' }));
    expect(
      screen.getByRole('heading', { name: 'Create your account.' }),
    ).toBeInTheDocument();
    expect(document.querySelector('.auth-page')).toHaveAttribute(
      'data-theme',
      'light',
    );
    fireEvent.click(screen.getByRole('button', { name: 'Log in' }));
    fireEvent.click(screen.getByRole('button', { name: 'Forgot password?' }));
    fireEvent.click(
      screen.getByRole('button', { name: 'Switch to dark mode' }),
    );
    expect(
      screen.getByRole('heading', { name: 'Reset your password.' }),
    ).toBeInTheDocument();
    expect(document.querySelector('.auth-page')).toHaveAttribute(
      'data-theme',
      'dark',
    );
    expect(localStorage.getItem('sprintflow-theme')).toBe('dark');
  });

  it('marks the current workspace page and provides a profile link in mobile navigation', async () => {
    mockSignedIn();
    render(<App />);
    const navigation = await screen.findByRole('navigation', {
      name: 'Main navigation',
    });
    const overview = within(navigation).getByRole('button', {
      name: 'Overview',
    });
    expect(overview).toHaveAttribute('aria-current', 'page');
    const tasks = within(navigation).getByRole('button', { name: 'Tasks' });
    fireEvent.click(tasks);
    expect(tasks).toHaveAttribute('aria-current', 'page');
    expect(overview).not.toHaveAttribute('aria-current');
    const profile = within(navigation).getByRole('button', { name: 'Profile' });
    fireEvent.click(profile);
    expect(
      screen.getByRole('heading', { name: 'Your profile' }),
    ).toBeInTheDocument();
    expect(profile).toHaveAttribute('aria-current', 'page');
    expect(tasks).not.toHaveAttribute('aria-current');
  });

  it('clearly explains the next steps when login credentials do not work', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(async (input: RequestInfo | URL) => {
        const url = String(input);
        if (url.endsWith('/auth/me') || url.endsWith('/projects'))
          return {
            ok: false,
            status: 401,
            json: async () => ({ message: 'Sign in to continue.' }),
          };
        if (url.endsWith('/auth/csrf'))
          return {
            ok: true,
            status: 200,
            json: async () => ({ token: 'csrf', headerName: 'X-CSRF-TOKEN' }),
          };
        if (url.endsWith('/auth/login'))
          return {
            ok: false,
            status: 401,
            json: async () => ({ message: 'Incorrect email or password.' }),
          };
        return { ok: true, status: 200, json: async () => ({}) };
      }),
    );
    render(<App />);
    fireEvent.change(await screen.findByLabelText('Email'), {
      target: { value: 'new@example.com' },
    });
    fireEvent.change(screen.getByLabelText('Password'), {
      target: { value: 'password1' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Log in' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'If you haven’t created an account yet, choose Create account.',
    );
    expect(screen.getByRole('alert')).toHaveTextContent(
      'check your email and password or reset your password',
    );
  });

  it('presents password recovery as a focused card and still sends the reset request', async () => {
    const requests: string[] = [];
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(async (input: RequestInfo | URL) => {
        const url = String(input);
        requests.push(url);
        if (url.endsWith('/auth/me') || url.endsWith('/projects'))
          return {
            ok: false,
            status: 401,
            json: async () => ({ message: 'Sign in to continue.' }),
          };
        if (url.endsWith('/auth/csrf'))
          return {
            ok: true,
            status: 200,
            json: async () => ({ token: 'csrf', headerName: 'X-CSRF-TOKEN' }),
          };
        if (url.endsWith('/auth/password/reset/request'))
          return {
            ok: true,
            status: 200,
            json: async () => ({
              message:
                'If the address belongs to an account, a reset link will be sent.',
            }),
          };
        return { ok: true, status: 200, json: async () => ({}) };
      }),
    );
    render(<App />);
    fireEvent.click(
      await screen.findByRole('button', { name: 'Forgot password?' }),
    );
    expect(
      await screen.findByRole('heading', { name: 'Reset your password.' }),
    ).toBeInTheDocument();
    expect(screen.getByText('ACCOUNT RECOVERY')).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: /Back to log in/ }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'Create account' }),
    ).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Email'), {
      target: { value: 'mouaz@example.com' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Send reset link' }));
    expect(
      await screen.findByText(
        'If the address belongs to an account, a reset link will be sent.',
      ),
    ).toBeInTheDocument();
    expect(requests).toContain('/api/auth/password/reset/request');
  });

  it('keeps log out inside the profile instead of the sidebar', async () => {
    mockSignedIn([]);
    render(<App />);

    fireEvent.click(
      await screen.findByRole('button', { name: /Leo.*Profile/i }),
    );

    expect(document.querySelector('.workspace-online')).toBeNull();
    expect(
      screen.getByRole('tab', { name: 'Personal details' }),
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
    expect(screen.getByRole('combobox', { name: 'Current project' })).toHaveValue('2');

    fireEvent.click(screen.getByRole('button', { name: 'Tasks' }));
    await waitFor(() => expect(requests).toContain('/api/tasks?projectId=2'));
    expect(screen.queryByRole('combobox', { name: 'Project' })).toBe(picker);

    fireEvent.click(screen.getByRole('button', { name: 'Backlog' }));
    await waitFor(() => expect(requests).toContain('/api/stories/project/2'));
    expect(
      screen.getByRole('combobox', { name: 'Project' }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('combobox', { name: 'Language' }),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Sprints' }));
    await waitFor(() => expect(requests).toContain('/api/sprints?projectId=2'));
    fireEvent.click(screen.getByRole('button', { name: 'Metrics' }));
    await waitFor(() => expect(requests).toContain('/api/projects/2/metrics'));
    fireEvent.click(screen.getByRole('button', { name: 'Updates' }));
    await waitFor(() => expect(requests).toContain('/api/projects/2/activity'));

    fireEvent.click(screen.getByRole('button', { name: 'Projects' }));
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

  it('lets project owners edit and delete activity updates', async () => {
    const requests: { url: string; method: string; body?: string }[] = [];
    let updates = [
      {
        id: 71,
        eventType: 'TASK_CREATED',
        summary: 'Created task “Draft plan”.',
        actorEmail: 'leo@example.com',
        createdAt: '2026-10-11T17:36:35Z',
      },
      {
        id: 72,
        eventType: 'PROJECT_CREATED',
        summary: 'Created project “Alpha”.',
        actorEmail: 'leo@example.com',
        createdAt: '2026-10-11T17:36:21Z',
      },
    ];

    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockImplementation(
          async (input: RequestInfo | URL, init: RequestInit = {}) => {
            const url = String(input);
            const method = init.method ?? 'GET';
            requests.push({
              url,
              method,
              body: init.body as string | undefined,
            });
            let payload: unknown = [];
            if (url.endsWith('/auth/me'))
              payload = { id: 1, email: 'leo@example.com', displayName: 'Leo' };
            else if (url.endsWith('/projects')) payload = projects.slice(0, 1);
            else if (url.endsWith('/projects/1/role'))
              payload = { role: 'Owner' };
            else if (
              url.endsWith('/projects/1/activity') &&
              method === 'DELETE'
            )
              updates = [];
            else if (
              url.endsWith('/projects/1/activity/71') &&
              method === 'PUT'
            ) {
              updates = updates.map((item) =>
                item.id === 71
                  ? { ...item, ...JSON.parse(String(init.body)) }
                  : item,
              );
              payload = updates[0];
            } else if (
              url.endsWith('/projects/1/activity/71') &&
              method === 'DELETE'
            )
              updates = updates.filter((item) => item.id !== 71);
            else if (url.endsWith('/projects/1/activity')) payload = updates;
            else if (url.endsWith('/auth/csrf'))
              payload = { token: 'csrf', headerName: 'X-CSRF-TOKEN' };
            return {
              ok: true,
              status: method === 'DELETE' ? 204 : 200,
              json: async () => payload,
            };
          },
        ),
    );
    render(<App />);
    fireEvent.click(await screen.findByRole('button', { name: 'Updates' }));
    const originalHeading = await screen.findByRole('heading', {
      name: 'Created task “Draft plan”.',
    });
    expect(originalHeading).toBeInTheDocument();
    fireEvent.click(
      within(originalHeading.closest('article') as HTMLElement).getByRole(
        'button',
        { name: 'Edit' },
      ),
    );
    fireEvent.change(screen.getByLabelText('Update summary'), {
      target: { value: 'Corrected update text' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));
    const correctedHeading = await screen.findByRole('heading', {
      name: 'Corrected update text',
    });
    expect(correctedHeading).toBeInTheDocument();
    expect(
      requests.some(
        (request) =>
          request.url.endsWith('/projects/1/activity/71') &&
          request.method === 'PUT' &&
          JSON.parse(request.body ?? '{}').summary === 'Corrected update text',
      ),
    ).toBe(true);
    fireEvent.click(
      within(correctedHeading.closest('article') as HTMLElement).getByRole(
        'button',
        { name: 'Delete' },
      ),
    );
    fireEvent.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', {
        name: 'Confirm',
      }),
    );
    expect(
      await screen.findByRole('heading', { name: 'Created project “Alpha”.' }),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Clear all history' }));
    fireEvent.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', {
        name: 'Confirm',
      }),
    );
    expect(
      await screen.findByText(
        'Project activity appears here when the team starts working.',
      ),
    ).toBeInTheDocument();
    expect(
      requests.some(
        (request) =>
          request.url.endsWith('/projects/1/activity/71') &&
          request.method === 'DELETE',
      ),
    ).toBe(true);
    expect(
      requests.some(
        (request) =>
          request.url.endsWith('/projects/1/activity') &&
          request.method === 'DELETE',
      ),
    ).toBe(true);
  });

  it('shows a useful empty state instead of a flat zero-point burndown', async () => {
    const zeroBurndown = Array.from({ length: 14 }, (_, index) => ({
      date: `2026-10-${String(index + 1).padStart(2, '0')}`,
      remainingPoints: 0,
    }));
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(async (input: RequestInfo | URL) => {
        const url = String(input);
        const payload = url.endsWith('/auth/me')
          ? { id: 1, email: 'leo@example.com', displayName: 'Leo' }
          : url.endsWith('/projects')
            ? projects.slice(0, 1)
            : url.includes('/metrics')
              ? {
                  taskCount: 1,
                  completedTasks: 1,
                  completionRate: 100,
                  throughputLast14Days: 1,
                  velocityStoryPoints: 3,
                  burndownAvailable: true,
                  burndown: zeroBurndown,
                  statusCounts: { Done: 1 },
                  sprintHealth: { status: 'Healthy' },
                }
              : [];
        return { ok: true, status: 200, json: async () => payload };
      }),
    );
    render(<App />);
    fireEvent.click(await screen.findByRole('button', { name: 'Metrics' }));
    expect(await screen.findByRole('status')).toHaveTextContent(
      'All work is complete',
    );
    expect(
      screen.queryByRole('img', { name: /remaining story points per day/i }),
    ).not.toBeInTheDocument();
    expect(screen.getByText('All work is complete')).toBeInTheDocument();
    expect(screen.getByText('0 points left')).toBeInTheDocument();
  });

  it('edits, moves, blocks and deletes tasks through the REST API', async () => {
    const requests: { url: string; method: string; body?: string }[] = [];
    let task = {
      id: 7,
      title: 'Ship board',
      description: 'First pass',
      status: 'To Do',
      projectId: 1,
      storyPoints: 3,
      version: 0,
    };

    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockImplementation(
          async (input: RequestInfo | URL, init: RequestInit = {}) => {
            const url = String(input);
            const method = init.method ?? 'GET';
            requests.push({
              url,
              method,
              body: init.body as string | undefined,
            });
            let payload: unknown = [];
            if (url.endsWith('/auth/me'))
              payload = { id: 1, email: 'leo@example.com', displayName: 'Leo' };
            else if (url.endsWith('/projects')) payload = projects.slice(0, 1);
            else if (url.includes('/tasks?projectId=1')) payload = [task];
            else if (url.endsWith('/auth/csrf'))
              payload = { token: 'csrf', headerName: 'X-CSRF-TOKEN' };
            else if (url.endsWith('/tasks/7') && method === 'PUT') {
              task = { ...task, ...JSON.parse(String(init.body)) };
              payload = task;
            } else if (url.includes('/tasks/7/block?'))
              payload = {
                ...task,
                blocked: true,
                blockReason: 'Waiting for review',
              };
            else if (url.endsWith('/tasks/7/unblock'))
              payload = { ...task, blocked: false };
            return {
              ok: true,
              status: method === 'DELETE' ? 204 : 200,
              json: async () => payload,
            };
          },
        ),
    );
    render(<App />);
    fireEvent.click(await screen.findByRole('button', { name: 'Tasks' }));
    expect(await screen.findByText('Ship board')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Search tasks'), {
      target: { value: 'does-not-exist' },
    });
    expect(screen.queryByText('Ship board')).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Search tasks'), {
      target: { value: 'Ship' },
    });
    expect(await screen.findByText('Ship board')).toBeInTheDocument();
    expect(screen.getByLabelText('Filter sprint')).toBeInTheDocument();
    expect(screen.getByLabelText('Filter assignee')).toBeInTheDocument();
    expect(screen.getByLabelText('Filter due date')).toBeInTheDocument();
    expect(screen.getByLabelText('Blocked')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Filter status'), {
      target: { value: 'Done' },
    });
    expect(screen.queryByText('Ship board')).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Filter status'), {
      target: { value: '' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Edit' }));
    fireEvent.change(screen.getByLabelText('Title'), {
      target: { value: 'Ship reliable board' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Save changes' }));
    await waitFor(() =>
      expect(
        requests.some(
          (request) =>
            request.url.endsWith('/tasks/7') &&
            request.method === 'PUT' &&
            JSON.parse(request.body ?? '{}').title === 'Ship reliable board',
        ),
      ).toBe(true),
    );
    await waitFor(() =>
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument(),
    );
    fireEvent.change(screen.getByLabelText('Move to'), {
      target: { value: 'In Progress' },
    });
    await waitFor(() =>
      expect(
        requests.some(
          (request) =>
            request.url.endsWith('/tasks/7') &&
            JSON.parse(request.body ?? '{}').status === 'In Progress',
        ),
      ).toBe(true),
    );
    const card = screen.getByText('Ship reliable board').closest('article');
    const reviewColumn = screen
      .getByText('Review', { selector: 'span' })
      .closest('section');
    fireEvent.dragStart(card!);
    fireEvent.drop(reviewColumn!, {
      dataTransfer: { setData: vi.fn(), getData: vi.fn() },
    });
    await waitFor(() =>
      expect(
        requests.some(
          (request) =>
            request.url.endsWith('/tasks/7') &&
            JSON.parse(request.body ?? '{}').status === 'Review',
        ),
      ).toBe(true),
    );
    fireEvent.click(screen.getByText('Attachments and links (0)'));
    fireEvent.change(screen.getByLabelText('Add link'), {
      target: { value: 'https://example.com/spec.pdf' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Save link' }));
    await waitFor(() =>
      expect(
        requests.some(
          (request) =>
            request.url.endsWith('/tasks/7/attachments') &&
            request.method === 'POST',
        ),
      ).toBe(true),
    );
    fireEvent.click(screen.getByRole('button', { name: 'Block' }));
    fireEvent.change(screen.getByLabelText('Block reason'), {
      target: { value: 'Waiting for review' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Block task' }));
    await waitFor(() =>
      expect(
        requests.some((request) =>
          request.url.includes('/tasks/7/block?reason=Waiting%20for%20review'),
        ),
      ).toBe(true),
    );
    fireEvent.click(await screen.findByRole('button', { name: 'Unblock' }));
    await screen.findByRole('button', { name: 'Block' });
    expect(
      requests.some((request) => request.url.endsWith('/tasks/7/unblock')),
    ).toBe(true);
    await waitFor(() =>
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument(),
    );
    fireEvent.click(screen.getByRole('button', { name: 'Delete' }));
    fireEvent.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', {
        name: 'Confirm',
      }),
    );
    await waitFor(() =>
      expect(
        requests.some(
          (request) =>
            request.url.endsWith('/workspace/trash/task/7') &&
            request.method === 'PUT',
        ),
      ).toBe(true),
    );
  });

  it('edits and deletes backlog stories and sprints', async () => {
    const requests: { url: string; method: string; body?: string }[] = [];
    let story: Record<string, unknown> | undefined = {
      id: 9,
      title: 'Write acceptance criteria',
      description: 'Clear outcomes',
      acceptanceCriteria: 'Must be verifiable',
      storyPoints: 3,
      priorityId: 2,
      projectId: 1,
      version: 0,
    };
    let sprint: Record<string, unknown> | undefined = {
      id: 4,
      name: 'Sprint 4',
      goal: 'Ship core flows',
      status: 'Planned',
      totalStoryPoints: 8,
      projectId: 1,
      version: 0,
    };

    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockImplementation(
          async (input: RequestInfo | URL, init: RequestInit = {}) => {
            const url = String(input);
            const method = init.method ?? 'GET';
            requests.push({
              url,
              method,
              body: init.body as string | undefined,
            });
            let payload: unknown = [];
            if (url.endsWith('/auth/me'))
              payload = { id: 1, email: 'leo@example.com', displayName: 'Leo' };
            else if (url.endsWith('/projects')) payload = projects.slice(0, 1);
            else if (url.includes('/stories/project/1'))
              payload = story ? [story] : [];
            else if (url.endsWith('/sprints?projectId=1'))
              payload = sprint ? [sprint] : [];
            else if (url.endsWith('/auth/csrf'))
              payload = { token: 'csrf', headerName: 'X-CSRF-TOKEN' };
            else if (url.endsWith('/stories/9') && method === 'PUT') {
              story = { ...story, ...JSON.parse(String(init.body)) };
              payload = story;
            } else if (
              url.endsWith('/workspace/trash/story/9') &&
              method === 'PUT'
            )
              story = undefined;
            else if (url.endsWith('/sprints/4') && method === 'PUT') {
              sprint = { ...sprint, ...JSON.parse(String(init.body)) };
              payload = sprint;
            } else if (
              url.endsWith('/workspace/trash/sprint/4') &&
              method === 'PUT'
            )
              sprint = undefined;
            return {
              ok: true,
              status: method === 'DELETE' ? 204 : 200,
              json: async () => payload,
            };
          },
        ),
    );
    render(<App />);
    fireEvent.click(await screen.findByRole('button', { name: 'Backlog' }));
    expect(
      await screen.findByRole('heading', { name: 'Write acceptance criteria' }),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Edit' }));
    await within(screen.getByRole('dialog')).findByRole('option', {
      name: 'Sprint 4',
    });
    const storyPointsOptions = Array.from(
      (screen.getByLabelText('Story points') as HTMLSelectElement).options,
    ).map((option) => option.value);
    expect(storyPointsOptions).toEqual(['1', '2', '3', '5', '8']);
    fireEvent.change(screen.getByLabelText('Title'), {
      target: { value: 'Define acceptance criteria' },
    });
    fireEvent.change(screen.getByLabelText('Business value (1–5)'), {
      target: { value: '5' },
    });
    fireEvent.change(screen.getByLabelText('Sprint'), {
      target: { value: '4' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Save changes' }));
    await waitFor(() =>
      expect(
        requests.some(
          (request) =>
            request.url.endsWith('/stories/9') && request.method === 'PUT',
        ),
      ).toBe(true),
    );
    expect(
      JSON.parse(
        requests.find(
          (request) =>
            request.url.endsWith('/stories/9') && request.method === 'PUT',
        )!.body!,
      ).businessValue,
    ).toBe(5);
    expect(
      JSON.parse(
        requests.find(
          (request) =>
            request.url.endsWith('/stories/9') && request.method === 'PUT',
        )!.body!,
      ).sprintId,
    ).toBe(4);
    await waitFor(() =>
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument(),
    );
    fireEvent.click(screen.getByRole('button', { name: 'Delete' }));
    fireEvent.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', {
        name: 'Confirm',
      }),
    );
    await waitFor(() =>
      expect(
        requests.some(
          (request) =>
            request.url.endsWith('/workspace/trash/story/9') &&
            request.method === 'PUT',
        ),
      ).toBe(true),
    );

    fireEvent.click(screen.getByRole('button', { name: 'Sprints' }));
    expect(
      await screen.findByRole('heading', { name: 'Sprint 4' }),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Edit' }));
    fireEvent.change(screen.getByLabelText('Name'), {
      target: { value: 'Sprint 5' },
    });
    fireEvent.change(screen.getByLabelText('Start date'), {
      target: { value: '2026-10-12' },
    });
    fireEvent.change(screen.getByLabelText('End date'), {
      target: { value: '2026-10-25' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Save changes' }));
    await waitFor(() =>
      expect(
        requests.some(
          (request) =>
            request.url.endsWith('/sprints/4') && request.method === 'PUT',
        ),
      ).toBe(true),
    );
    const sprintUpdate = JSON.parse(
      requests.find(
        (request) =>
          request.url.endsWith('/sprints/4') && request.method === 'PUT',
      )!.body!,
    );
    expect(sprintUpdate.startDate).toBe('2026-10-12');
    expect(sprintUpdate.endDate).toBe('2026-10-25');
    await waitFor(() =>
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument(),
    );
    fireEvent.click(screen.getByRole('button', { name: 'Delete' }));
    fireEvent.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', {
        name: 'Confirm',
      }),
    );
    await waitFor(() =>
      expect(
        requests.some(
          (request) =>
            request.url.endsWith('/workspace/trash/sprint/4') &&
            request.method === 'PUT',
        ),
      ).toBe(true),
    );
  });

  it('edits and deletes projects, members, and accounts through their REST endpoints', async () => {
    const requests: { url: string; method: string; body?: string }[] = [];
    let projectList = [{ ...projects[0], version: 0 }];
    let memberList = [{ id: 2, email: 'sam@example.com', role: 'Member' }];

    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockImplementation(
          async (input: RequestInfo | URL, init: RequestInit = {}) => {
            const url = String(input);
            const method = init.method ?? 'GET';
            requests.push({
              url,
              method,
              body: init.body as string | undefined,
            });
            let payload: unknown = [];
            if (url.endsWith('/auth/me'))
              payload = { id: 1, email: 'leo@example.com', displayName: 'Leo' };
            else if (url.endsWith('/projects')) payload = projectList;
            else if (url.endsWith('/projects/1/members')) payload = memberList;
            else if (url.endsWith('/auth/csrf'))
              payload = { token: 'csrf', headerName: 'X-CSRF-TOKEN' };
            else if (url.endsWith('/projects/1') && method === 'PUT') {
              projectList = [
                { ...projectList[0], ...JSON.parse(String(init.body)) },
              ];
              payload = projectList[0];
            } else if (
              url.endsWith('/workspace/trash/project/1') &&
              method === 'PUT'
            )
              projectList = [];
            else if (
              url.endsWith('/projects/1/members/sam%40example.com') &&
              method === 'DELETE'
            )
              memberList = [];
            else if (url.endsWith('/auth/account') && method === 'DELETE')
              payload = { message: 'Account deleted.' };
            return {
              ok: true,
              status: method === 'DELETE' ? 204 : 200,
              json: async () => payload,
            };
          },
        ),
    );
    render(<App />);
    fireEvent.click(await screen.findByRole('button', { name: 'Projects' }));
    expect(
      await screen.findByRole('heading', { name: 'Alpha' }),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Edit' }));
    fireEvent.change(screen.getByLabelText('Project name'), {
      target: { value: 'Alpha v2' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Save project' }));
    await waitFor(() =>
      expect(
        requests.some(
          (request) =>
            request.url.endsWith('/projects/1') && request.method === 'PUT',
        ),
      ).toBe(true),
    );
    fireEvent.click(screen.getByRole('button', { name: 'Remove' }));
    fireEvent.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', {
        name: 'Confirm',
      }),
    );
    await waitFor(() =>
      expect(
        requests.some(
          (request) =>
            request.url.endsWith('/projects/1/members/sam%40example.com') &&
            request.method === 'DELETE',
        ),
      ).toBe(true),
    );
    await waitFor(() =>
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument(),
    );
    fireEvent.click(screen.getByRole('button', { name: 'Delete' }));
    fireEvent.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', {
        name: 'Confirm',
      }),
    );
    await waitFor(() =>
      expect(
        requests.some(
          (request) =>
            request.url.endsWith('/workspace/trash/project/1') &&
            request.method === 'PUT',
        ),
      ).toBe(true),
    );

    fireEvent.click(screen.getByRole('button', { name: /Leo.*Profile/i }));
    fireEvent.click(screen.getByRole('tab', { name: 'Security' }));
    fireEvent.change(screen.getByLabelText('Current password'), {
      target: { value: 'old-password' },
    });
    fireEvent.change(screen.getByLabelText('New password'), {
      target: { value: 'new-password-123' },
    });
    fireEvent.change(screen.getByLabelText('Confirm new password'), {
      target: { value: 'new-password-123' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Update password' }));
    await waitFor(() =>
      expect(
        requests.some(
          (request) =>
            request.url.endsWith('/auth/password/change') &&
            request.method === 'POST',
        ),
      ).toBe(true),
    );
    fireEvent.click(screen.getByText('Delete account'));
    fireEvent.change(screen.getByLabelText('Confirm with password'), {
      target: { value: 'new-password-123' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Delete my account' }));
    fireEvent.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', {
        name: 'Confirm',
      }),
    );
    await waitFor(() =>
      expect(
        requests.some(
          (request) =>
            request.url.endsWith('/auth/account') &&
            request.method === 'DELETE',
        ),
      ).toBe(true),
    );
  });
});
