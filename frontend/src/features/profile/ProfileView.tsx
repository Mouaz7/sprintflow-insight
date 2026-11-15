import { PageHeader } from '../../shared/ui/ui';
import { PreferencesPanel } from './PreferencesPanel';
import { ProfileDetails } from './ProfileDetails';
import { Feedback, SettingsIcon } from './ProfileFeedback';
import { DeleteAccountSettings, PasswordSettings } from './ProfileSecurity';
import { useProfile, type ProfileViewProps } from './useProfile';
export function ProfileView(props: ProfileViewProps) {
  const controller = useProfile(props);
  const {
    language,
    sv,
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
                if (next === undefined) return;
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
        <ProfileDetails controller={controller} />
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
