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
describe('App.projects', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    vi.unstubAllGlobals();
  });
  it('edits and deletes projects, members, and accounts through their REST endpoints', async () => {
    const requests: {
      url: string;
      method: string;
      body?: string | undefined;
    }[] = [];
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
