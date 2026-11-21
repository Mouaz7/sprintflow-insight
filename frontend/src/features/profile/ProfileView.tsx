import { lazy, Suspense } from 'react';
import { useT } from '../../shared/i18n/LanguageProvider';
import { PageHeader } from '../../shared/ui/ui';
import { profileText } from './i18n';
import { PreferencesPanel } from './PreferencesPanel';
import { ProfileDetails } from './ProfileDetails';
import { Feedback, SettingsIcon } from './ProfileFeedback';
import { DeleteAccountSettings, PasswordSettings } from './ProfileSecurity';
import { useProfile, type ProfileViewProps } from './useProfile';
const TwoFactorSettings = lazy(() => import('./TwoFactorSettings'));

export function ProfileView(props: ProfileViewProps) {
  const controller = useProfile(props);
  const t = useT(profileText);
  const {
    loggingOut,
    saving,
    logout,
    user,
    tab,
    setTab,
    logoutError,
    onAppearance,
    onReplay,
    onAccountDeleted,
  } = controller;
  return (
    <section className="page profile-page">
      <PageHeader
        guide="profile"
        eyebrow={t('accountSettings')}
        title={t('title')}
        description={t('description')}
        action={
          <button
            type="button"
            className="secondary-action profile-signout"
            disabled={loggingOut || saving}
            onClick={logout}
          >
            <SettingsIcon kind="logout" />
            {t(loggingOut ? 'loggingOut' : 'logOut')}
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
        <span className="status-badge">{user.jobTitle || t('teamMember')}</span>
      </div>
      <div
        className="profile-tabs"
        role="tablist"
        aria-label={t('profileSettings')}
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
                if (next === undefined) return;
                setTab(next);
                document.getElementById('profile-tab-' + next)?.focus();
              }
            }}
          >
            {t(
              key === 'details'
                ? 'tabDetails'
                : key === 'security'
                  ? 'tabSecurity'
                  : 'tabSettings',
            )}
          </button>
        ))}
      </div>
      <Feedback error={logoutError} />
      {tab === 'settings' && (
        <div
          id="profile-panel-settings"
          role="tabpanel"
          aria-labelledby="profile-tab-settings"
        >
          <PreferencesPanel onAppearance={onAppearance} onReplay={onReplay} />
        </div>
      )}
      <div
        className="profile-layout"
        id="profile-panel-details"
        role="tabpanel"
        aria-labelledby="profile-tab-details"
        hidden={tab !== 'details'}
      >
        <ProfileDetails controller={controller} />
      </div>
      <div
        className="profile-security-layout"
        id="profile-panel-security"
        role="tabpanel"
        aria-labelledby="profile-tab-security"
        hidden={tab !== 'security'}
      >
        <PasswordSettings email={user.email} />
        {tab === 'security' && (
          <Suspense>
            <TwoFactorSettings />
          </Suspense>
        )}
        <DeleteAccountSettings
          email={user.email}
          onDeleted={onAccountDeleted}
        />
      </div>
    </section>
  );
}
