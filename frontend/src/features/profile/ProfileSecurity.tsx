import type { FormEvent } from 'react';
import { useState } from 'react';
import { api } from '../../api/client';
import { useT } from '../../shared/i18n/LanguageProvider';
import { useConfirm } from '../../shared/ui/ui';
import { profileText } from './i18n';
import { Feedback, SettingsIcon } from './ProfileFeedback';

export function PasswordSettings({ email }: { email: string }) {
  const t = useT(profileText);
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
      setError(t('passwordsDiffer'));
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
      setMessage(t('passwordChanged'));
    } catch (cause) {
      setError(
        cause instanceof Error ? cause.message : t('passwordChangeFailed'),
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
          <h2 id="profile-password-title">{t('changePassword')}</h2>
          <p>{t('keepSecure')}</p>
        </div>
      </div>
      <fieldset className="profile-fields" disabled={busy}>
        <legend className="sr-only">{t('passwordDetails')}</legend>
        <label>
          {t('currentPassword')}
          <input
            required
            type={visible ? 'text' : 'password'}
            autoComplete="current-password"
            value={currentPassword}
            placeholder={t('currentPasswordPlaceholder')}
            onChange={(event) => {
              setCurrentPassword(event.target.value);
              clearFeedback();
            }}
          />
        </label>
        <label>
          {t('newPassword')}
          <input
            required
            minLength={8}
            maxLength={72}
            type={visible ? 'text' : 'password'}
            autoComplete="new-password"
            value={newPassword}
            placeholder={t('newPasswordPlaceholder')}
            onChange={(event) => {
              setNewPassword(event.target.value);
              clearFeedback();
            }}
            aria-describedby="profile-password-help"
          />
        </label>
        <p id="profile-password-help" className="profile-field-help">
          {t('passwordHelp')}
        </p>
        <label>
          {t('confirmPassword')}
          <input
            required
            minLength={8}
            maxLength={72}
            type={visible ? 'text' : 'password'}
            autoComplete="new-password"
            value={confirmation}
            placeholder={t('confirmPasswordPlaceholder')}
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
          {t(visible ? 'hidePasswords' : 'showPasswords')}
        </button>
      </fieldset>
      <Feedback error={error} message={message} />
      <div className="profile-card-footer">
        <button className="primary-action" disabled={busy}>
          {t(busy ? 'updating' : 'updatePassword')}
        </button>
      </div>
    </form>
  );
}

export function DeleteAccountSettings({
  email,
  onDeleted,
}: {
  email: string;
  onDeleted: () => void;
}) {
  const t = useT(profileText);
  const confirm = useConfirm();
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (busy || !(await confirm(t('confirmDeleteAccount')))) return;
    setBusy(true);
    setError('');
    try {
      await api('/auth/account', {
        method: 'DELETE',
        body: JSON.stringify({ email, currentPassword: password }),
      });
      onDeleted();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t('deleteFailed'));
    } finally {
      setBusy(false);
    }
  }
  return (
    <details className="profile-delete-section">
      <summary>
        <span>
          <strong>{t('deleteAccount')}</strong>
          <small>{t('deleteAccountHelp')}</small>
        </span>
        <span className="profile-delete-chevron" aria-hidden="true">
          ⌄
        </span>
      </summary>
      <form onSubmit={submit} aria-busy={busy}>
        <p className="profile-delete-warning">{t('deleteWarning')}</p>
        <label>
          {t('confirmWithPassword')}
          <input
            required
            disabled={busy}
            type="password"
            autoComplete="current-password"
            value={password}
            placeholder={t('confirmWithPasswordPlaceholder')}
            onChange={(event) => {
              setPassword(event.target.value);
              setError('');
            }}
          />
        </label>
        <Feedback error={error} />
        <button className="danger-button" disabled={busy}>
          {t(busy ? 'deletingAccount' : 'deleteMyAccount')}
        </button>
      </form>
    </details>
  );
}
