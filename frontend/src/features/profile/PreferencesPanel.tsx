import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { api } from '../../api/client';
import type { Language, Preferences } from '../../api/contracts';
import { preferencesQuery } from '../../api/queries';
import { useLanguage, useT } from '../../shared/i18n/LanguageProvider';
import { translator } from '../../shared/i18n/lexicon';
import { Notice } from '../../shared/ui/ui';
import { profileText } from './i18n';
import { AccountExport } from './AccountExport';
import { DigestSettings } from './DigestSettings';
import { integrationsQuery } from './queries';
export function PreferencesPanel({
  onAppearance,
  onReplay,
}: {
  onAppearance?:
    ((language: Language, theme: 'light' | 'dark') => void) | undefined;
  onReplay?: (() => void) | undefined;
}) {
  const { language } = useLanguage();
  const t = useT(profileText);
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
      // Confirmed in the language that was just saved.
      setSuccess(translator(profileText, value.language)('settingsSaved'));
    } catch (cause) {
      setError((cause as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="profile-layout">
      <form className="profile-card" onSubmit={save}>
        <h2>{t('appearance')}</h2>
        <fieldset disabled={busy || !ready}>
          <label>
            {t('language')}
            <select
              aria-label={t('language')}
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
            {t('theme')}
            <select
              aria-label={t('theme')}
              value={settings.theme}
              onChange={(event) =>
                setSettings((value) => ({
                  ...value,
                  theme: event.target.value as Preferences['theme'],
                }))
              }
            >
              <option value="light">{t('light')}</option>
              <option value="dark">{t('dark')}</option>
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
            {t('receiveNotifications')}
          </label>
          <button className="primary-action">{t('saveSettings')}</button>
        </fieldset>
        <Notice error={error} success={success} />
        <button className="secondary-action" type="button" onClick={onReplay}>
          {t('replayIntroduction')}
        </button>
      </form>
      <DigestSettings />
      <AccountExport />
      <section className="profile-card">
        <h2>{t('accountServices')}</h2>
        <p>
          Gmail · {t(integrations?.gmailReady ? 'configured' : 'setupRequired')}
        </p>
        <p>
          Google OAuth ·{' '}
          {t(integrations?.googleReady ? 'configured' : 'setupRequired')}
        </p>
        <p>{t('servicesHelp')}</p>
      </section>
    </div>
  );
}
