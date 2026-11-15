import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client';
import { ProfileView } from './ProfileView';

vi.mock('../../api/client', () => ({ api: vi.fn() }));

const user = {
  id: 1,
  email: 'leo@example.com',
  displayName: 'Leo',
  jobTitle: 'Developer',
  bio: 'Building SprintFlow.',
};
const props = {
  user,
  language: 'EN' as const,
  onSaved: vi.fn(),
  onLogout: vi.fn().mockResolvedValue(undefined),
  onAccountDeleted: vi.fn(),
};

describe('Profile settings', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api).mockReset().mockResolvedValue({});
  });

  it('uses keyboard tabs and preserves the personal draft across security settings', () => {
    render(<ProfileView {...props} />);
    fireEvent.change(screen.getByLabelText('Name'), {
      target: { value: 'Unsaved name' },
    });
    const security = screen.getByRole('tab', { name: 'Security' });
    security.focus();
    fireEvent.keyDown(security, { key: 'ArrowLeft' });
    expect(screen.getByRole('tab', { name: 'Personal details' })).toHaveFocus();
    fireEvent.keyDown(document.activeElement!, { key: 'End' });
    expect(screen.getByRole('tab', { name: 'Settings' })).toHaveAttribute(
      'aria-selected',
      'true',
    );
    fireEvent.keyDown(screen.getByRole('tab', { name: 'Settings' }), {
      key: 'Home',
    });
    expect(screen.getByLabelText('Name')).toHaveValue('Unsaved name');
  });

  it('discards edits and prevents duplicate submissions while a profile save is pending', async () => {
    let finishSave!: (value: typeof user) => void;
    vi.mocked(api).mockImplementation(
      () =>
        new Promise((resolve) => {
          finishSave = resolve;
        }),
    );
    render(<ProfileView {...props} />);
    expect(screen.getByRole('button', { name: 'Save profile' })).toBeDisabled();
    fireEvent.change(screen.getByLabelText('Name'), {
      target: { value: 'Temporary name' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Discard changes' }));
    expect(screen.getByLabelText('Name')).toHaveValue('Leo');
    expect(screen.getByRole('button', { name: 'Save profile' })).toBeDisabled();

    fireEvent.change(screen.getByLabelText('Name'), {
      target: { value: '  Leo Updated  ' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Save profile' }));
    expect(screen.getByRole('button', { name: 'Saving…' })).toBeDisabled();
    expect(screen.getByLabelText('Name')).toBeDisabled();
    fireEvent.submit(screen.getByRole('form', { name: 'Personal details' }));
    expect(api).toHaveBeenCalledTimes(1);
    expect(
      JSON.parse(vi.mocked(api).mock.calls[0]![1]!.body as string),
    ).toMatchObject({ displayName: 'Leo Updated', email: user.email });
    finishSave({ ...user, displayName: 'Leo Updated' });
    expect(await screen.findByRole('status')).toHaveTextContent(
      'Profile saved.',
    );
    expect(props.onSaved).toHaveBeenCalledWith({
      ...user,
      displayName: 'Leo Updated',
    });
  });

  it('keeps password validation and server errors in the password card and clears fields after success', async () => {
    render(<ProfileView {...props} />);
    fireEvent.click(screen.getByRole('tab', { name: 'Security' }));
    const passwordCard = screen.getByRole('form', { name: 'Change password' });
    fireEvent.change(screen.getByLabelText('Current password'), {
      target: { value: 'wrong-password' },
    });
    fireEvent.change(screen.getByLabelText('New password'), {
      target: { value: 'new-password-123' },
    });
    fireEvent.change(screen.getByLabelText('Confirm new password'), {
      target: { value: 'different-password' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Update password' }));
    expect(within(passwordCard).getByRole('alert')).toHaveTextContent(
      'Your new passwords do not match.',
    );
    expect(api).not.toHaveBeenCalled();
    expect(
      within(
        document.querySelector('.profile-details-card') as HTMLElement,
      ).queryByRole('alert'),
    ).not.toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Confirm new password'), {
      target: { value: 'new-password-123' },
    });
    vi.mocked(api).mockRejectedValueOnce(
      new Error('Current password is incorrect.'),
    );
    fireEvent.click(screen.getByRole('button', { name: 'Update password' }));
    expect(await within(passwordCard).findByRole('alert')).toHaveTextContent(
      'Current password is incorrect.',
    );
    fireEvent.click(screen.getByRole('button', { name: 'Show passwords' }));
    expect(screen.getByLabelText('New password')).toHaveAttribute(
      'type',
      'text',
    );
    fireEvent.change(screen.getByLabelText('Current password'), {
      target: { value: 'correct-password' },
    });
    vi.mocked(api).mockResolvedValueOnce({});
    fireEvent.click(screen.getByRole('button', { name: 'Update password' }));
    expect(await within(passwordCard).findByRole('status')).toHaveTextContent(
      'Password changed.',
    );
    expect(screen.getByLabelText('Current password')).toHaveValue('');
    expect(screen.getByLabelText('New password')).toHaveValue('');
    expect(screen.getByLabelText('Confirm new password')).toHaveValue('');
    expect(screen.getByLabelText('New password')).toHaveAttribute(
      'type',
      'password',
    );
  });

  it('previews valid photos, rejects unsupported files and saves removal of an existing photo', async () => {
    const avatarImage = 'data:image/png;base64,cGhvdG8=';
    vi.mocked(api).mockResolvedValue({ ...user, avatarImage: '' });
    render(<ProfileView {...props} user={{ ...user, avatarImage }} />);
    const picker = screen.getByLabelText('Choose profile photo');
    fireEvent.change(picker, {
      target: {
        files: [new File(['text'], 'notes.txt', { type: 'text/plain' })],
      },
    });
    expect(screen.getByRole('alert')).toHaveTextContent(
      'Choose a PNG, JPEG, WEBP or GIF under 750 KB.',
    );
    expect(
      screen.getByRole('img', { name: 'Profile photo preview' }),
    ).toHaveAttribute('src', avatarImage);
    fireEvent.change(picker, {
      target: {
        files: [new File(['new-photo'], 'photo.png', { type: 'image/png' })],
      },
    });
    await waitFor(() =>
      expect(
        screen
          .getByRole('img', { name: 'Profile photo preview' })
          .getAttribute('src'),
      ).not.toBe(avatarImage),
    );
    fireEvent.click(screen.getByRole('button', { name: 'Remove photo' }));
    expect(
      screen.queryByRole('img', { name: 'Profile photo preview' }),
    ).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Save profile' }));
    expect(await screen.findByRole('status')).toHaveTextContent(
      'Profile saved.',
    );
    expect(
      JSON.parse(vi.mocked(api).mock.calls[0]![1]!.body as string).avatarImage,
    ).toBe('');
  });

  it('shows ownership errors inside the expanded account removal section', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    vi.mocked(api).mockRejectedValueOnce(
      new Error('Transfer owned projects before deleting your account.'),
    );
    render(<ProfileView {...props} />);
    fireEvent.click(screen.getByRole('tab', { name: 'Security' }));
    expect(
      screen.getByText('Delete account').closest('details'),
    ).not.toHaveAttribute('open');
    fireEvent.click(screen.getByText('Delete account'));
    fireEvent.change(screen.getByLabelText('Confirm with password'), {
      target: { value: 'password-123' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Delete my account' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Transfer owned projects before deleting your account.',
    );
    expect(screen.getByRole('alert').closest('details')).toBeInTheDocument();
    expect(props.onAccountDeleted).not.toHaveBeenCalled();
    expect(
      screen.getByRole('button', { name: 'Delete my account' }),
    ).toBeEnabled();
  });
});
