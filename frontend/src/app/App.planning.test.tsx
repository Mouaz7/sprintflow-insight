import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from './App';
import { projects } from './appTestFixtures';
describe('App.planning', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    vi.unstubAllGlobals();
  });
  it('edits and deletes backlog stories and sprints', async () => {
    const requests: {
      url: string;
      method: string;
      body?: string | undefined;
    }[] = [];
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
});
