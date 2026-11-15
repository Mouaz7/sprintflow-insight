import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client';
import type { Language, Project } from '../../api/contracts';
import { ProjectsView } from './ProjectsView';
import { ConfirmProvider } from '../../shared/ui/ui';

vi.mock('../../api/client', () => ({ api: vi.fn() }));

const user = { id: 1, email: 'qa@example.com' };

function projectsView(projects: Project[], language: Language = 'EN') {
  const onChanged = vi.fn().mockResolvedValue(undefined);
  render(
    <ConfirmProvider language={language}>
      <ProjectsView
        projects={projects}
        user={user}
        language={language}
        onChanged={onChanged}
        onSelect={vi.fn()}
      />
    </ConfirmProvider>,
  );
  return onChanged;
}

function lastBody() {
  const call = vi.mocked(api).mock.calls.at(-1)!;
  return {
    path: call[0],
    init: call[1]!,
    body: JSON.parse(String(call[1]!.body)),
  };
}

describe('Project keys', () => {
  beforeEach(() => {
    vi.mocked(api).mockReset();
  });

  it('suggests a key from the name and sends the chosen key in upper case', async () => {
    vi.mocked(api).mockResolvedValue({
      id: 3,
      name: 'Sprint Flow',
      key: 'NYK',
    });
    projectsView([]);
    fireEvent.click(screen.getByRole('button', { name: /Create project/ }));
    fireEvent.change(screen.getByLabelText('Project name'), {
      target: { value: 'Sprint Flow' },
    });
    const key = screen.getByRole('textbox', { name: 'Project key' });
    expect(key).toHaveAttribute('placeholder', 'SF');
    expect(screen.getByText(/for example SF-1\./)).toBeInTheDocument();

    fireEvent.change(key, { target: { value: 'nyk' } });
    expect(key).toHaveValue('NYK');
    fireEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', {
        name: 'Create project',
      }),
    );
    await waitFor(() => expect(api).toHaveBeenCalledOnce());
    expect(lastBody().path).toBe('/projects');
    expect(lastBody().body).toMatchObject({ name: 'Sprint Flow', key: 'NYK' });
  });

  it('leaves the key to the server when the field is empty', async () => {
    vi.mocked(api).mockResolvedValue({ id: 3, name: 'Apollo', key: 'APOL' });
    projectsView([]);
    fireEvent.click(screen.getByRole('button', { name: /Create project/ }));
    fireEvent.change(screen.getByLabelText('Project name'), {
      target: { value: 'Apollo' },
    });
    fireEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', {
        name: 'Create project',
      }),
    );
    await waitFor(() => expect(api).toHaveBeenCalledOnce());
    expect(lastBody().body).not.toHaveProperty('key');
  });

  it('rejects an invalid key before writing, in Swedish too', async () => {
    projectsView([], 'SV');
    fireEvent.click(screen.getByRole('button', { name: /Skapa projekt/ }));
    fireEvent.change(screen.getByLabelText('Projektnamn'), {
      target: { value: 'Åre Östra' },
    });
    const key = screen.getByRole('textbox', { name: 'Projektnyckel' });
    expect(key).toHaveAttribute('placeholder', 'AO');
    fireEvent.change(key, { target: { value: 'A1' } });
    fireEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', {
        name: 'Skapa projekt',
      }),
    );
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Projektnyckeln måste bestå av 2–10 bokstäver mellan A och Z',
    );
    expect(api).not.toHaveBeenCalled();
  });

  it('shows keys on project cards and edits them with the current version', async () => {
    const project = {
      id: 4,
      name: 'Sprint Flow',
      key: 'SF',
      ownerEmail: user.email,
      version: 2,
    };
    vi.mocked(api).mockResolvedValue({ ...project, key: 'FLOW', version: 3 });
    projectsView([project]);
    expect(
      screen.getByRole('heading', { name: 'SF Sprint Flow' }),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Edit' }));
    const key = screen.getByRole('textbox', { name: 'Project key' });
    expect(key).toHaveValue('SF');
    fireEvent.change(key, { target: { value: 'flow' } });
    expect(
      screen.getByText(
        /Changing the key renames every task ID, for example FLOW-12\./,
      ),
    ).toBeInTheDocument();
    fireEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', {
        name: 'Save project',
      }),
    );
    await waitFor(() => expect(api).toHaveBeenCalledOnce());
    const { path, init, body } = lastBody();
    expect(path).toBe('/projects/4');
    expect(init.method).toBe('PUT');
    expect(init.headers).toEqual({ 'If-Match': '"2"' });
    expect(body).toMatchObject({ name: 'Sprint Flow', key: 'FLOW' });
  });

  it('filters projects by key', () => {
    projectsView([
      { id: 1, name: 'Alpha', key: 'ALP', ownerEmail: user.email },
      { id: 2, name: 'Beta', key: 'BET', ownerEmail: user.email },
    ]);
    fireEvent.change(
      screen.getByRole('searchbox', { name: 'Search projects' }),
      {
        target: { value: 'bet' },
      },
    );
    expect(
      screen.queryByRole('heading', { name: 'ALP Alpha' }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole('heading', { name: 'BET Beta' }),
    ).toBeInTheDocument();
  });
});
