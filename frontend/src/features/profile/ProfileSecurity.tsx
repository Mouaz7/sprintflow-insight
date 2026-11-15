import { useState } from 'react';
import type { FormEvent } from 'react';
import { api } from '../../api/client';
import { useConfirm } from '../../shared/ui/ui';
import { SettingsIcon, Feedback } from './ProfileFeedback';

export function PasswordSettings({
  email,
  sv,
}: {
  email: string;
  sv: boolean;
}) {
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [visible, setVisible] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  function clearFeedback() {
    setError('');
    setMessage('');
  }
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (busy) return;
    clearFeedback();
    if (newPassword !== confirmation) {
      setError(
        sv
          ? 'De nya lösenorden stämmer inte överens.'
          : 'Your new passwords do not match.',
      );
      return;
    }
    setBusy(true);
    try {
      await api('/auth/password/change', {
        method: 'POST',
        body: JSON.stringify({ email, currentPassword, newPassword }),
      });
      setCurrentPassword('');
      setNewPassword('');
      setConfirmation('');
      setVisible(false);
      setMessage(sv ? 'Lösenordet har ändrats.' : 'Password changed.');
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : sv
            ? 'Lösenordet kunde inte ändras. Försök igen.'
            : 'Could not change your password. Please try again.',
      );
    } finally {
      setBusy(false);
    }
  }
  return (
    <form
      className="profile-card profile-password-card"
      onSubmit={submit}
      aria-labelledby="profile-password-title"
      aria-busy={busy}
    >
      <div className="profile-card-heading">
        <span className="profile-card-icon">
          <SettingsIcon kind="lock" />
        </span>
        <div>
          <h2 id="profile-password-title">
            {sv ? 'Byt lösenord' : 'Change password'}
          </h2>
          <p>{sv ? 'Håll ditt konto säkert.' : 'Keep your account secure.'}</p>
        </div>
      </div>
      <fieldset className="profile-fields" disabled={busy}>
        <legend className="sr-only">
          {sv ? 'Lösenordsuppgifter' : 'Password details'}
        </legend>
        <label>
          {sv ? 'Nuvarande lösenord' : 'Current password'}
          <input
            required
            type={visible ? 'text' : 'password'}
            autoComplete="current-password"
            value={currentPassword}
            placeholder={sv ? 'Ditt nuvarande inloggningslösenord' : 'Your current sign-in password'}
            onChange={(event) => {
              setCurrentPassword(event.target.value);
              clearFeedback();
            }}
          />
        </label>
        <label>
          {sv ? 'Nytt lösenord' : 'New password'}
          <input
            required
            minLength={8}
            maxLength={72}
            type={visible ? 'text' : 'password'}
            autoComplete="new-password"
            value={newPassword}
            placeholder={sv ? 'Välj ett eget lösenord med 8–72 tecken' : 'Choose your own password with 8–72 characters'}
            onChange={(event) => {
              setNewPassword(event.target.value);
              clearFeedback();
            }}
            aria-describedby="profile-password-help"
          />
        </label>
        <p id="profile-password-help" className="profile-field-help">
          {sv
            ? 'Använd 8–72 tecken och välj ett unikt lösenord.'
            : 'Use 8–72 characters and choose a unique password.'}
        </p>
        <label>
          {sv ? 'Bekräfta nytt lösenord' : 'Confirm new password'}
          <input
            required
            minLength={8}
            maxLength={72}
            type={visible ? 'text' : 'password'}
            autoComplete="new-password"
            value={confirmation}
            placeholder={sv ? 'Skriv samma nya lösenord igen' : 'Enter the same new password again'}
            onChange={(event) => {
              setConfirmation(event.target.value);
              clearFeedback();
            }}
          />
        </label>
        <button
          type="button"
          className="profile-text-button profile-password-toggle"
          aria-pressed={visible}
          onClick={() => setVisible((value) => !value)}
        >
          {visible
            ? sv
              ? 'Dölj lösenord'
              : 'Hide passwords'
            : sv
              ? 'Visa lösenord'
              : 'Show passwords'}
        </button>
      </fieldset>
      <Feedback sv={sv} error={error} message={message} />
      <div className="profile-card-footer">
        <button className="primary-action" disabled={busy}>
          {busy
            ? sv
              ? 'Uppdaterar…'
              : 'Updating…'
            : sv
              ? 'Uppdatera lösenord'
              : 'Update password'}
        </button>
      </div>
    </form>
  );
}

export function DeleteAccountSettings({
  email,
  sv,
  onDeleted,
}: {
  email: string;
  sv: boolean;
  onDeleted: () => void;
}) {
  const confirm = useConfirm();
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (
      busy ||
      !(await confirm(
        sv
          ? 'Kontot och dess personuppgifter tas bort. Överför eller ta bort dina ägda projekt först. Fortsätta?'
          : 'Your account and personal data will be deleted. Transfer or delete owned projects first. Continue?',
      ))
    )
      return;
    setBusy(true);
    setError('');
    try {
      await api('/auth/account', {
        method: 'DELETE',
        body: JSON.stringify({ email, currentPassword: password }),
      });
      onDeleted();
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : sv
            ? 'Kontot kunde inte tas bort. Försök igen.'
            : 'Could not delete your account. Please try again.',
      );
    } finally {
      setBusy(false);
    }
  }
  return (
    <details className="profile-delete-section">
      <summary>
        <span>
          <strong>{sv ? 'Ta bort konto' : 'Delete account'}</strong>
          <small>
            {sv
              ? 'Hantera permanent borttagning av ditt konto.'
              : 'Manage permanent removal of your account.'}
          </small>
        </span>
        <span className="profile-delete-chevron" aria-hidden="true">
          ⌄
        </span>
      </summary>
      <form onSubmit={submit} aria-busy={busy}>
        <p className="profile-delete-warning">
          {sv
            ? 'Det går inte att ångra. Överför eller ta bort alla projekt du äger innan du fortsätter.'
            : 'This cannot be undone. Transfer or delete all projects you own before continuing.'}
        </p>
        <label>
          {sv ? 'Bekräfta med lösenord' : 'Confirm with password'}
          <input
            required
            disabled={busy}
            type="password"
            autoComplete="current-password"
            value={password}
            placeholder={sv ? 'Ditt nuvarande lösenord bekräftar borttagningen' : 'Your current password confirms deletion'}
            onChange={(event) => {
              setPassword(event.target.value);
              setError('');
            }}
          />
        </label>
        <Feedback sv={sv} error={error} />
        <button className="danger-button" disabled={busy}>
          {busy
            ? sv
              ? 'Tar bort konto…'
              : 'Deleting account…'
            : sv
              ? 'Ta bort mitt konto'
              : 'Delete my account'}
        </button>
      </form>
    </details>
  );
}
