import { FormGuide, fieldExample } from './Guidance';
import { SettingsIcon, Feedback } from './ProfileFeedback';
import { PasswordSettings, DeleteAccountSettings } from './ProfileSecurity';
import { PreferencesPanel } from './PreferencesPanel';
import { useEffect, useRef, useState } from 'react';
import type { ChangeEvent, FormEvent } from 'react';
import { PageHeader } from './ui';
import { api } from '../api/client';
import type { User } from '../api/contracts';

type ProfileProps = {
  user: User;
  language: 'EN' | 'SV';
  onSaved: (user: User) => void;
  onLogout: () => Promise<void>;
  onAccountDeleted: () => void;
  onAppearance?: (language: 'EN' | 'SV', theme: 'light' | 'dark') => void;
  onReplay?: () => void;
};

export function ProfileView({
  user,
  language,
  onSaved,
  onLogout,
  onAccountDeleted,
  onAppearance,
  onReplay,
}: ProfileProps) {
  const sv = language === 'SV';
  const [tab, setTab] = useState<'details' | 'security' | 'settings'>(
    'details',
  );
  const [displayName, setDisplayName] = useState(user.displayName || '');
  const [jobTitle, setJobTitle] = useState(user.jobTitle || '');
  const [bio, setBio] = useState(user.bio || '');
  const [avatarImage, setAvatarImage] = useState(user.avatarImage || '');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [readingImage, setReadingImage] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);
  const [logoutError, setLogoutError] = useState('');
  const fileInput = useRef<HTMLInputElement>(null);
  const imageReader = useRef<FileReader | null>(null);
  const dirty =
    displayName !== (user.displayName || '') ||
    jobTitle !== (user.jobTitle || '') ||
    bio !== (user.bio || '') ||
    avatarImage !== (user.avatarImage || '');

  useEffect(() => {
    setDisplayName(user.displayName || '');
    setJobTitle(user.jobTitle || '');
    setBio(user.bio || '');
    setAvatarImage(user.avatarImage || '');
  }, [user.displayName, user.jobTitle, user.bio, user.avatarImage]);

  useEffect(
    () => () => {
      if (imageReader.current) {
        imageReader.current.onload = null;
        imageReader.current.onerror = null;
        imageReader.current.onabort = null;
        imageReader.current.abort();
      }
    },
    [],
  );

  function clearFeedback() {
    setMessage('');
    setError('');
  }
  function discardChanges() {
    setDisplayName(user.displayName || '');
    setJobTitle(user.jobTitle || '');
    setBio(user.bio || '');
    setAvatarImage(user.avatarImage || '');
    clearFeedback();
  }

  function selectImage(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;
    clearFeedback();
    const invalidImage = sv
      ? 'Välj PNG, JPEG, WEBP eller GIF under 750 kB.'
      : 'Choose a PNG, JPEG, WEBP or GIF under 750 KB.';
    if (
      !['image/png', 'image/jpeg', 'image/webp', 'image/gif'].includes(
        file.type,
      ) ||
      file.size > 750_000
    ) {
      setError(invalidImage);
      return;
    }
    imageReader.current?.abort();
    const reader = new FileReader();
    imageReader.current = reader;
    setReadingImage(true);
    reader.onload = () => {
      const result = String(reader.result);
      if (result.length > 1_000_000) setError(invalidImage);
      else setAvatarImage(result);
      setReadingImage(false);
    };
    reader.onerror = () => {
      setReadingImage(false);
      setError(
        sv
          ? 'Bilden kunde inte läsas. Välj en annan bild.'
          : 'We couldn’t read this photo. Choose another image.',
      );
    };
    reader.onabort = () => setReadingImage(false);
    reader.readAsDataURL(file);
  }

  async function saveProfile(event: FormEvent) {
    event.preventDefault();
    if (saving || readingImage || !dirty) return;
    if (!displayName.trim()) {
      setError(sv ? 'Ange ditt namn.' : 'Enter your name.');
      return;
    }
    setSaving(true);
    clearFeedback();
    try {
      const updated = await api<User>('/auth/profile', {
        method: 'PUT',
        body: JSON.stringify({
          currentEmail: user.email,
          email: user.email,
          displayName: displayName.trim(),
          jobTitle: jobTitle.trim(),
          bio,
          avatarImage,
          recoveryCode: null,
        }),
      });
      onSaved(updated);
      setMessage(sv ? 'Profilen har sparats.' : 'Profile saved.');
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : sv
            ? 'Profilen kunde inte sparas. Försök igen.'
            : 'Could not save your profile. Please try again.',
      );
    } finally {
      setSaving(false);
    }
  }

  async function logout() {
    if (loggingOut) return;
    setLoggingOut(true);
    setLogoutError('');
    try {
      await onLogout();
    } catch (cause) {
      setLogoutError(
        cause instanceof Error
          ? cause.message
          : sv
            ? 'Det gick inte att logga ut. Försök igen.'
            : 'Could not log out. Please try again.',
      );
      setLoggingOut(false);
    }
  }

  return (
    <section className="page profile-page">
      <PageHeader
        guide="profile"
        language={language}
        eyebrow={sv ? 'KONTOINSTÄLLNINGAR' : 'ACCOUNT SETTINGS'}
        title={sv ? 'Din profil' : 'Your profile'}
        description={
          sv
            ? 'Dina uppgifter. Din arbetsyta.'
            : 'Your details. Your workspace.'
        }
        action={
          <button
            type="button"
            className="secondary-action profile-signout"
            disabled={loggingOut || saving}
            onClick={logout}
          >
            <SettingsIcon kind="logout" />
            {loggingOut
              ? sv
                ? 'Loggar ut…'
                : 'Logging out…'
              : sv
                ? 'Logga ut'
                : 'Log out'}
          </button>
        }
      />
      <div className="profile-identity">
        <span className="profile-identity-avatar">
          {user.avatarImage ? (
            <img src={user.avatarImage} alt="" />
          ) : (
            (user.displayName || user.email).slice(0, 1).toUpperCase()
          )}
        </span>
        <div>
          <h2>{user.displayName || user.email}</h2>
          <p>{user.email}</p>
        </div>
        <span className="status-badge">
          {user.jobTitle || (sv ? 'Teammedlem' : 'Team member')}
        </span>
      </div>
      <div
        className="profile-tabs"
        role="tablist"
        aria-label={sv ? 'Profilinställningar' : 'Profile settings'}
      >
        {(['details', 'security', 'settings'] as const).map((key) => (
          <button
            type="button"
            role="tab"
            key={key}
            id={'profile-tab-' + key}
            aria-controls={'profile-panel-' + key}
            aria-selected={tab === key}
            tabIndex={tab === key ? 0 : -1}
            onClick={() => setTab(key)}
            onKeyDown={(e) => {
              if (['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(e.key)) {
                e.preventDefault();
                const next =
                  e.key === 'Home'
                    ? 'details'
                    : e.key === 'End'
                      ? 'settings'
                      : (['details', 'security', 'settings'] as const)[
                          ((
                            ['details', 'security', 'settings'] as const
                          ).indexOf(key) +
                            (e.key === 'ArrowRight' ? 1 : 2)) %
                            3
                        ];
                setTab(next);
                document.getElementById('profile-tab-' + next)?.focus();
              }
            }}
          >
            {key === 'details'
              ? sv
                ? 'Personliga uppgifter'
                : 'Personal details'
              : key === 'security'
                ? sv
                  ? 'Säkerhet'
                  : 'Security'
                : sv
                  ? 'Inställningar'
                  : 'Settings'}
          </button>
        ))}
      </div>
      <Feedback sv={sv} error={logoutError} />
      {tab === 'settings' && (
        <div
          id="profile-panel-settings"
          role="tabpanel"
          aria-labelledby="profile-tab-settings"
        >
          <PreferencesPanel
            language={language}
            onAppearance={onAppearance}
            onReplay={onReplay}
          />
        </div>
      )}
      <div
        className="profile-layout"
        id="profile-panel-details"
        role="tabpanel"
        aria-labelledby="profile-tab-details"
        hidden={tab !== 'details'}
      >
        <form
          className="profile-card profile-details-card"
          onSubmit={saveProfile}
          aria-labelledby="profile-details-title"
          aria-busy={saving || readingImage}
        >
          <div className="profile-card-heading">
            <span className="profile-card-icon">
              <SettingsIcon kind="person" />
            </span>
            <div>
              <h2 id="profile-details-title">
                {sv ? 'Personliga uppgifter' : 'Personal details'}
              </h2>
              <p>
                {sv
                  ? 'Så här ser ditt team dig i SprintFlow.'
                  : 'How your team sees you in SprintFlow.'}
              </p>
            </div>
          </div>
          <fieldset className="profile-fields" disabled={saving}>
            <legend className="sr-only">
              {sv ? 'Profiluppgifter' : 'Profile details'}
            </legend>
            <FormGuide topic="profile" language={language} />
            <div className="profile-photo-row">
              <div className="profile-avatar">
                {avatarImage ? (
                  <img
                    src={avatarImage}
                    alt={
                      sv
                        ? 'Förhandsvisning av profilbild'
                        : 'Profile photo preview'
                    }
                  />
                ) : (
                  (displayName.trim() || user.email).slice(0, 1).toUpperCase()
                )}
              </div>
              <div className="profile-photo-controls">
                <strong>{sv ? 'Profilbild' : 'Profile photo'}</strong>
                <div className="profile-photo-actions">
                  <button
                    type="button"
                    className="secondary-action"
                    disabled={readingImage}
                    onClick={() => fileInput.current?.click()}
                  >
                    {readingImage
                      ? sv
                        ? 'Läser bild…'
                        : 'Reading photo…'
                      : sv
                        ? 'Ändra bild'
                        : 'Change photo'}
                  </button>
                  {avatarImage && (
                    <button
                      type="button"
                      className="profile-text-button"
                      disabled={readingImage}
                      onClick={() => {
                        setAvatarImage('');
                        clearFeedback();
                      }}
                    >
                      {sv ? 'Ta bort bild' : 'Remove photo'}
                    </button>
                  )}
                </div>
                <small>
                  {sv
                    ? 'PNG, JPEG, WEBP eller GIF · under 750 kB'
                    : 'PNG, JPEG, WEBP or GIF · under 750 KB'}
                </small>
                <input
                  ref={fileInput}
                  type="file"
                  hidden
                  aria-label={sv ? 'Välj profilbild' : 'Choose profile photo'}
                  accept="image/png,image/jpeg,image/webp,image/gif"
                  onChange={selectImage}
                />
              </div>
            </div>
            <div className="profile-field-row">
              <label>
                {sv ? 'Namn' : 'Name'}
                <input
                  required
                  maxLength={80}
                  autoComplete="name"
                  value={displayName}
                  onChange={(event) => {
                    setDisplayName(event.target.value);
                    clearFeedback();
                  }}
                  placeholder={fieldExample('name', language)}
                />
              </label>
              <label>
                {sv ? 'Roll' : 'Role'}
                <input
                  maxLength={80}
                  autoComplete="organization-title"
                  value={jobTitle}
                  onChange={(event) => {
                    setJobTitle(event.target.value);
                    clearFeedback();
                  }}
                  placeholder={fieldExample('jobTitle', language)}
                />
              </label>
            </div>
            <label>
              {sv ? 'E-post' : 'Email'}
              <input
                type="email"
                value={user.email}
                readOnly
                aria-describedby="profile-email-help"
              />
            </label>
            <p id="profile-email-help" className="profile-field-help">
              {sv
                ? 'Din e-postadress används för inloggning och kan inte ändras här.'
                : 'Your email is used to sign in and cannot be changed here.'}
            </p>
            <label>
              {sv ? 'Om dig' : 'About you'}
              <textarea
                aria-label={sv ? 'Om dig' : 'About you'}
                maxLength={500}
                rows={4}
                value={bio}
                onChange={(event) => {
                  setBio(event.target.value);
                  clearFeedback();
                }}
                placeholder={fieldExample('bio', language)}
                aria-describedby="profile-bio-count"
              />
            </label>
            <span id="profile-bio-count" className="profile-character-count">
              {bio.length} / 500
            </span>
          </fieldset>
          <Feedback sv={sv} error={error} message={message} />
          <div className="profile-card-footer">
            <span className="profile-save-hint">
              {dirty
                ? sv
                  ? 'Du har osparade ändringar'
                  : 'You have unsaved changes'
                : sv
                  ? 'Din profil är uppdaterad'
                  : 'Your profile is up to date'}
            </span>
            <div className="profile-form-actions">
              {dirty && (
                <button
                  type="button"
                  className="profile-text-button"
                  disabled={saving || readingImage}
                  onClick={discardChanges}
                >
                  {sv ? 'Ångra ändringar' : 'Discard changes'}
                </button>
              )}
              <button
                className="primary-action"
                disabled={saving || readingImage || !dirty}
              >
                {saving
                  ? sv
                    ? 'Sparar…'
                    : 'Saving…'
                  : sv
                    ? 'Spara profil'
                    : 'Save profile'}
              </button>
            </div>
          </div>
        </form>
      </div>
      <div
        className="profile-security-layout"
        id="profile-panel-security"
        role="tabpanel"
        aria-labelledby="profile-tab-security"
        hidden={tab !== 'security'}
      >
        <PasswordSettings email={user.email} sv={sv} />
        <DeleteAccountSettings
          email={user.email}
          sv={sv}
          onDeleted={onAccountDeleted}
        />
      </div>
    </section>
  );
}
