import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client';
import { ConfirmProvider } from '../../shared/ui/ui';
import { ProjectsView } from './ProjectsView';

vi.mock('../../api/client', () => ({ api: vi.fn() }));
const props = {
  projects: [],
  user: { id: 1, email: 'qa@example.com' },
  language: 'EN' as const,
  onSelect: vi.fn(),
};
describe('Project editor', () => {
  beforeEach(() => vi.clearAllMocks());
  async function createDraft(onChanged: () => Promise<void>) {
    render(<ProjectsView {...props} onChanged={onChanged} />);
    fireEvent.click(screen.getByRole('button', { name: /Create project/ }));
    fireEvent.change(screen.getByLabelText('Project name'), {
      target: { value: 'QA draft' },
    });
    fireEvent.change(screen.getByLabelText('Description'), {
      target: { value: 'Keep my contents' },
    });
    fireEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', {
        name: 'Create project',
      }),
    );
  }
  it('retains both fields after a rejected write', async () => {
    vi.mocked(api).mockRejectedValueOnce(
      new Error('You do not have permission to create this project.'),
    );
    await createDraft(vi.fn());
    expect(await screen.findByRole('alert')).toHaveTextContent('permission');
    expect(screen.getByLabelText('Project name')).toHaveValue('QA draft');
    expect(screen.getByLabelText('Description')).toHaveValue(
      'Keep my contents',
    );
    expect(
      within(screen.getByRole('dialog')).getByRole('button', {
        name: 'Create project',
      }),
    ).toBeEnabled();
  });
  it('selects the newly created project after refreshing the list', async () => {
    vi.mocked(api).mockResolvedValueOnce({ id: 9, name: 'QA draft' });
    const onChanged = vi.fn().mockResolvedValue(undefined);
    await createDraft(onChanged);
    await waitFor(() =>
      expect(props.onSelect).toHaveBeenCalledExactlyOnceWith(9),
    );
    expect(onChanged).toHaveBeenCalledOnce();
  });
  it('retries a failed refresh without writing the same project twice', async () => {
    vi.mocked(api).mockResolvedValueOnce({
      id: 7,
      name: 'QA draft',
      description: 'Keep my contents',
      ownerEmail: props.user.email,
    });
    const onChanged = vi
      .fn()
      .mockRejectedValueOnce(new Error('The project list could not refresh.'))
      .mockResolvedValueOnce(undefined);
    await createDraft(onChanged);
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'could not refresh',
    );
    fireEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', {
        name: 'Create project',
      }),
    );
    await waitFor(() =>
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument(),
    );
    expect(api).toHaveBeenCalledTimes(1);
    expect(onChanged).toHaveBeenCalledTimes(2);
    expect(props.onSelect).toHaveBeenCalledExactlyOnceWith(7);
  });
});

describe('Project deletion', () => {
  const project = {
    id: 77,
    name: 'Deletion QA',
    ownerEmail: props.user.email,
    version: 3,
  };
  beforeEach(() => vi.clearAllMocks());
  it('explains cascading deletion, supports cancellation, and deletes with one request after confirmation', async () => {
    vi.mocked(api).mockResolvedValue(undefined);
    const onChanged = vi.fn().mockResolvedValue(undefined);
    render(
      <ConfirmProvider>
        <ProjectsView {...props} projects={[project]} onChanged={onChanged} />
      </ConfirmProvider>,
    );
    fireEvent.click(screen.getByRole('button', { name: 'Delete' }));
    expect(screen.getByRole('alertdialog')).toHaveTextContent(
      'All its contents are retained and can be restored.',
    );
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(api).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: 'Delete' }));
    fireEvent.click(screen.getByRole('button', { name: 'Confirm' }));
    await waitFor(() => expect(onChanged).toHaveBeenCalledTimes(1));
    expect(api).toHaveBeenCalledTimes(1);
    expect(api).toHaveBeenCalledWith('/workspace/trash/project/77', {
      method: 'PUT',
      headers: { 'If-Match': '"3"' },
      body: JSON.stringify({ restore: false }),
    });
  });
  it('updates an existing server error immediately when the language changes', async () => {
    vi.mocked(api).mockRejectedValue(
      new Error('Only the project owner can delete this project.'),
    );
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const onChanged = vi.fn();
    const view = render(
      <ProjectsView {...props} projects={[project]} onChanged={onChanged} />,
    );
    fireEvent.click(screen.getByRole('button', { name: 'Delete' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Only the project owner',
    );
    view.rerender(
      <ProjectsView
        {...props}
        language="SV"
        projects={[project]}
        onChanged={onChanged}
      />,
    );
    expect(screen.getByRole('alert')).toHaveTextContent(
      'Endast projektägaren kan ta bort projektet.',
    );
    view.rerender(
      <ProjectsView {...props} projects={[project]} onChanged={onChanged} />,
    );
    expect(screen.getByRole('alert')).toHaveTextContent(
      'Only the project owner',
    );
    expect(onChanged).not.toHaveBeenCalled();
  });
  it('disables project deletion for users who are not the owner', () => {
    render(
      <ProjectsView
        {...props}
        projects={[{ ...project, ownerEmail: 'someone-else@example.com' }]}
        onChanged={vi.fn()}
      />,
    );
    expect(screen.getByRole('button', { name: 'Delete' })).toBeDisabled();
  });
  it('uses singular English and counts filtered projects', () => {
    render(
      <ProjectsView {...props} projects={[project]} onChanged={vi.fn()} />,
    );
    expect(screen.getByText('1 project')).toBeInTheDocument();
    fireEvent.change(screen.getByRole('searchbox'), {
      target: { value: 'no match' },
    });
    expect(screen.getByText('0 projects')).toBeInTheDocument();
  });
});
