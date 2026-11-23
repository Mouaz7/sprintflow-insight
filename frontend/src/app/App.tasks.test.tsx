import { HttpResponse } from 'msw';
import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from './App';
import { mockApi } from '../test/api';
import { projects } from './appTestFixtures';
describe('App.tasks', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    vi.unstubAllGlobals();
  });
  it('edits, moves, blocks and deletes tasks through the REST API', async () => {
    const requests: {
      url: string;
      method: string;
      body?: string | undefined;
    }[] = [];
    let task = {
      id: 7,
      title: 'Ship board',
      description: 'First pass',
      status: 'To Do',
      projectId: 1,
      storyPoints: 3,
      version: 0,
    };

    mockApi(async (input, init) => {
      const url = String(input);
      const method = init.method ?? 'GET';
      requests.push({
        url,
        method,
        body: init.body,
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
      return method === 'DELETE'
        ? new HttpResponse(null, { status: 204 })
        : payload;
    });
    render(<App />);
    fireEvent.click(await screen.findByRole('link', { name: 'Tasks' }));
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
    expect(screen.getByLabelText('Manually blocked')).toBeInTheDocument();
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
      .getByRole('heading', { level: 2, name: 'Review' })
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
});
