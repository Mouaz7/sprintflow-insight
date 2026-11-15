import { fireEvent, render, screen, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from './App';
import { projects } from './appTestFixtures';
describe('App.activity', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    vi.unstubAllGlobals();
  });
  it('lets project owners edit and delete activity updates', async () => {
    const requests: {
      url: string;
      method: string;
      body?: string | undefined;
    }[] = [];
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
});
