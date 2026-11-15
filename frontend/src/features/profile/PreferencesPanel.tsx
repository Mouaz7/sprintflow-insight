import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { api } from '../../api/client';
import type { Language, Preferences } from '../../api/contracts';
import { preferencesQuery } from '../../api/queries';
import { Notice } from '../../shared/ui/ui';
import { integrationsQuery } from './queries';
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
  const queryClient = useQueryClient();
  const preferences = useQuery(preferencesQuery);
  const integrations = useQuery(integrationsQuery).data;
  const [fallback] = useState<Preferences>(() => ({
    language,
    theme:
      localStorage.getItem('sprintflow-theme') === 'dark' ? 'dark' : 'light',
    notifications: true,
    onboardingCompleted: false,
    savedFilters: '[]',
  }));
  // Unsaved edits; until the first edit the form shows the saved settings.
  const [draft, setDraft] = useState<Preferences>();
  const saved =
    preferences.data && ['EN', 'SV'].includes(preferences.data.language)
      ? preferences.data
      : fallback;
  const settings = draft ?? saved;
  const setSettings = (update: (value: Preferences) => Preferences) =>
    setDraft(update(settings));
  const ready = preferences.isSuccess;
  const [writeError, setError] = useState('');
  const error = writeError || preferences.error?.message || '';
  const [success, setSuccess] = useState('');
  const [busy, setBusy] = useState(false);
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
      // The workspace shell applies saved appearance from this cache entry.
      queryClient.setQueryData(preferencesQuery.queryKey, value);
      setDraft(undefined);
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
