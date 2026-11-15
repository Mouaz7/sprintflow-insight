import { useEffect, useState, type FormEvent } from 'react';
import { api } from '../../api/client';
import type { Language, Preferences } from '../../api/contracts';
import { Notice } from '../../shared/ui/ui';
export function PreferencesPanel({
  language,
  onAppearance,
  onReplay,
}: {
  language: Language;
  onAppearance?:
    ((language: Language, theme: 'light' | 'dark') => void) | undefined;
  onReplay?: (() => void) | undefined;
}) {
  const sv = language === 'SV';
  const [settings, setSettings] = useState<Preferences>({
    language,
    theme:
      localStorage.getItem('sprintflow-theme') === 'dark' ? 'dark' : 'light',
    notifications: true,
    onboardingCompleted: false,
    savedFilters: '[]',
  });
  const [ready, setReady] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [busy, setBusy] = useState(false);
  const [integrations, setIntegrations] = useState<{
    gmailReady: boolean;
    googleReady: boolean;
  }>();
  useEffect(() => {
    let live = true;
    api<Preferences>('/auth/preferences')
      .then((value) => {
        if (live) {
          if (value && ['EN', 'SV'].includes(value.language))
            setSettings(value);
          setReady(true);
        }
      })
      .catch((cause) => {
        if (live) setError(cause.message);
      });
    api<{ gmailReady: boolean; googleReady: boolean }>('/auth/integrations')
      .then((value) => {
        if (live) setIntegrations(value);
      })
      .catch(() => {});
    return () => {
      live = false;
    };
  }, []);
  async function save(event: FormEvent) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setError('');
    setSuccess('');
    try {
      const value = await api<Preferences>('/auth/preferences', {
        method: 'PUT',
        body: JSON.stringify({
          language: settings.language,
          theme: settings.theme,
          notifications: settings.notifications,
        }),
      });
      setSettings(value);
      localStorage.setItem('sprintflow-language', value.language);
      localStorage.setItem('sprintflow-theme', value.theme);
      onAppearance?.(value.language, value.theme);
      window.dispatchEvent(new Event('sprintflow:notifications'));
      setSuccess(
        value.language === 'SV'
          ? 'Inställningarna är sparade.'
          : 'Settings saved.',
      );
    } catch (cause) {
      setError((cause as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="profile-layout">
      <form className="profile-card" onSubmit={save}>
        <h2>{sv ? 'Utseende och notiser' : 'Appearance and notifications'}</h2>
        <fieldset disabled={busy || !ready}>
          <label>
            {sv ? 'Språk' : 'Language'}
            <select
              aria-label={sv ? 'Språk' : 'Language'}
              value={settings.language}
              onChange={(event) =>
                setSettings((value) => ({
                  ...value,
                  language: event.target.value as Language,
                }))
              }
            >
              <option value="EN">English</option>
              <option value="SV">Svenska</option>
            </select>
          </label>
          <label>
            {sv ? 'Tema' : 'Theme'}
            <select
              aria-label={sv ? 'Tema' : 'Theme'}
              value={settings.theme}
              onChange={(event) =>
                setSettings((value) => ({
                  ...value,
                  theme: event.target.value as Preferences['theme'],
                }))
              }
            >
              <option value="light">{sv ? 'Ljust' : 'Light'}</option>
              <option value="dark">{sv ? 'Mörkt' : 'Dark'}</option>
            </select>
          </label>
          <label className="checkbox-row">
            <input
              type="checkbox"
              checked={settings.notifications}
              onChange={(event) =>
                setSettings((value) => ({
                  ...value,
                  notifications: event.target.checked,
                }))
              }
            />
            {sv
              ? 'Ta emot samarbetsnotiser och deadlinepåminnelser'
              : 'Receive collaboration notifications and deadline reminders'}
          </label>
          <button className="primary-action">
            {sv ? 'Spara inställningar' : 'Save settings'}
          </button>
        </fieldset>
        <Notice language={language} error={error} success={success} />
        <button className="secondary-action" type="button" onClick={onReplay}>
          {sv ? 'Visa introduktionen igen' : 'Replay introduction'}
        </button>
      </form>
      <section className="profile-card">
        <h2>{sv ? 'Kontotjänster' : 'Account services'}</h2>
        <p>
          Gmail ·{' '}
          {integrations?.gmailReady
            ? sv
              ? 'Konfigurerad'
              : 'Configured'
            : sv
              ? 'Behöver konfigureras'
              : 'Setup required'}
        </p>
        <p>
          Google OAuth ·{' '}
          {integrations?.googleReady
            ? sv
              ? 'Konfigurerad'
              : 'Configured'
            : sv
              ? 'Behöver konfigureras'
              : 'Setup required'}
        </p>
        <p>
          {sv
            ? 'Administratören konfigurerar tjänsterna på servern. Lösenord och nycklar sparas aldrig i din profil.'
            : 'The administrator configures these services on the server. Passwords and keys are never stored in your profile.'}
        </p>
      </section>
    </div>
  );
}
