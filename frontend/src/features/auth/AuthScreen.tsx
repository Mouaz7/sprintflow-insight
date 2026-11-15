import { useState, type FormEvent } from 'react';
import { useMatch, useNavigate, useSearchParams } from 'react-router';
import { api, ApiError, clearCsrfToken } from '../../api/client';
import type { User } from '../../api/contracts';
import { useFieldExample } from '../../shared/ui/Guidance';
import { InterfaceIcon } from '../../shared/ui/InterfaceIcons';
import { useLanguage, useT } from '../../shared/i18n/LanguageProvider';
import { useTheme } from '../../shared/theme/ThemeProvider';
import { LanguageSelector } from '../../shared/ui/LanguageSelector';
import { Notice } from '../../shared/ui/ui';
import { GoogleSignIn } from './GoogleSignIn';
import { authText } from './i18n';

type AuthMode = 'login' | 'register' | 'reset' | 'new-password';
export function AuthScreen({
  onSignedIn,
  initialError,
}: {
  onSignedIn: (user: User) => Promise<void>;
  initialError: string;
}) {
  const navigate = useNavigate();
  const resetRoute = useMatch('/reset-password');
  const [searchParams] = useSearchParams();
  const [mode, setMode] = useState<AuthMode>(() =>
    resetRoute ? 'new-password' : 'login',
  );
  const [displayName, setDisplayName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [resetToken] = useState(() => searchParams.get('token') ?? '');
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('');
  const [error, setError] = useState(initialError);
  const { theme, setTheme } = useTheme();
  const darkMode = theme === 'dark';
  const setDarkMode = (dark: boolean) => setTheme(dark ? 'dark' : 'light');
  const { language, setLanguage } = useLanguage();
  const t = useT(authText);
  const fieldExample = useFieldExample();
  function changeMode(next: AuthMode) {
    if (busy) return;
    // The reset token leaves the address bar once the reset form is left.
    if (resetRoute) void navigate('/', { replace: true });
    setMode(next);
    setNotice('');
    setError('');
    setPassword('');
    setConfirmPassword('');
  }
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setNotice('');
    setError('');
    if (mode === 'new-password' && password !== confirmPassword) {
      setError(t('passwordsDiffer'));
      setBusy(false);
      return;
    }
    try {
      if (mode === 'new-password') {
        await api('/auth/password/reset/confirm', {
          method: 'POST',
          body: JSON.stringify({ token: resetToken, newPassword: password }),
        });
        setPassword('');
        setConfirmPassword('');
        setNotice(t('passwordUpdated'));
      } else if (mode === 'reset') {
        const response = await api<{ message: string }>(
          '/auth/password/reset/request',
          { method: 'POST', body: JSON.stringify({ email: email.trim() }) },
        );
        setNotice(t('resetRequested', { serverMessage: response.message }));
      } else {
        const user = await api<User>(
          mode === 'login' ? '/auth/login' : '/auth/register',
          {
            method: 'POST',
            body: JSON.stringify({
              email: email.trim(),
              password,
              ...(mode === 'register'
                ? { displayName: displayName.trim() }
                : {}),
            }),
          },
        );
        clearCsrfToken();
        await onSignedIn(user);
      }
    } catch (cause) {
      const loginFailed =
        mode === 'login' && cause instanceof ApiError && cause.status === 401;
      setError(
        loginFailed
          ? 'We couldn’t sign you in. If you haven’t created an account yet, choose Create account. If you already have an account, check your email and password or reset your password.'
          : cause instanceof Error
            ? cause.message
            : t('requestFailed'),
      );
    } finally {
      setBusy(false);
    }
  }
  const title = t(
    (
      {
        login: 'titleLogin',
        register: 'titleRegister',
        reset: 'titleReset',
        'new-password': 'titleNewPassword',
      } as const
    )[mode],
  );
  const subtitle = t(
    (
      {
        login: 'subtitleLogin',
        register: 'subtitleRegister',
        reset: 'subtitleReset',
        'new-password': 'subtitleNewPassword',
      } as const
    )[mode],
  );
  return (
    <main className="auth-page" data-theme={darkMode ? 'dark' : 'light'}>
      <div className="auth-frame">
        <aside className="auth-story">
          <div className="auth-story-heading">
            <span className="eyebrow">SPRINTFLOW / WORKSPACE</span>
            <h1>{t('storyTitle')}</h1>
            <p>{t('storyText')}</p>
          </div>
          <div className="auth-example" aria-label={t('exampleWorkspace')}>
            <header>
              <span className="status-badge">{t('example')}</span>
              <span>01 / 03</span>
            </header>
            <h2>{t('nextRelease')}</h2>
            <p>{t('nextReleaseText')}</p>
            <div className="example-progress">
              <span />
            </div>
            <div className="example-progress-label">
              <strong>68%</strong>
              <span>{t('planComplete')}</span>
            </div>
            <div className="example-task">
              <span className="example-check">✓</span>
              <div>
                <strong>{t('sharedGoal')}</strong>
                <small>{t('sharedGoalStatus')}</small>
              </div>
            </div>
            <div className="example-task">
              <span className="example-dot" />
              <div>
                <strong>{t('nextExperience')}</strong>
                <small>{t('nextExperienceStatus')}</small>
              </div>
            </div>
          </div>
          <footer>
            <span>01</span>
            <p>{t('storyFooter')}</p>
          </footer>
        </aside>
        <section className="auth-panel">
          <header className="auth-brand-row">
            <div className="brand">
              <span className="brand-mark">
                <InterfaceIcon name="tasks" />
              </span>
              <strong>SprintFlow</strong>
            </div>
            <div className="auth-settings">
              <LanguageSelector language={language} onChange={setLanguage} />
              <button
                type="button"
                className="theme-toggle"
                aria-label={t(darkMode ? 'switchToLight' : 'switchToDark')}
                onClick={() => setDarkMode(!darkMode)}
              >
                <InterfaceIcon name={darkMode ? 'sun' : 'moon'} />
              </button>
            </div>
          </header>
          <div className="auth-card">
            <span className="eyebrow">
              {t(
                mode === 'reset' || mode === 'new-password'
                  ? 'accountRecovery'
                  : 'yourWorkspace',
              )}
            </span>
            <h2>{title}</h2>
            <p className="auth-subtitle">{subtitle}</p>
            <form onSubmit={submit} aria-busy={busy}>
              <fieldset disabled={busy}>
                {mode === 'register' && (
                  <label>
                    {t('name')}
                    <input
                      required
                      maxLength={80}
                      autoComplete="name"
                      value={displayName}
                      onChange={(e) => setDisplayName(e.target.value)}
                      placeholder={fieldExample('name')}
                    />
                  </label>
                )}
                {mode !== 'new-password' && (
                  <label>
                    {t('email')}
                    <input
                      required
                      type="email"
                      autoComplete="email"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      placeholder="you@example.com"
                    />
                  </label>
                )}
                {mode !== 'reset' && (
                  <label>
                    {t('password')}
                    <input
                      required
                      minLength={6}
                      maxLength={72}
                      type="password"
                      autoComplete={
                        mode === 'login' ? 'current-password' : 'new-password'
                      }
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      placeholder={t(
                        mode === 'login'
                          ? 'existingPassword'
                          : 'choosePassword',
                      )}
                    />
                  </label>
                )}
                {mode === 'new-password' && (
                  <label>
                    {t('confirmPassword')}
                    <input
                      required
                      minLength={6}
                      maxLength={72}
                      type="password"
                      autoComplete="new-password"
                      value={confirmPassword}
                      placeholder={t('confirmPasswordPlaceholder')}
                      onChange={(e) => setConfirmPassword(e.target.value)}
                    />
                  </label>
                )}
              </fieldset>
              {mode === 'login' && (
                <button
                  type="button"
                  className="text-button forgot-link"
                  disabled={busy}
                  onClick={() => changeMode('reset')}
                >
                  {t('forgotPassword')}
                </button>
              )}
              <Notice error={error} success={notice} />
              <button className="primary-action" disabled={busy}>
                {t(
                  busy
                    ? 'pleaseWait'
                    : mode === 'login'
                      ? 'logIn'
                      : mode === 'register'
                        ? 'createAccount'
                        : mode === 'new-password'
                          ? 'saveNewPassword'
                          : 'sendResetLink',
                )}
              </button>
            </form>
            {mode !== 'reset' && mode !== 'new-password' && (
              <>
                <div className="divider">
                  <span>{t('or')}</span>
                </div>
                <GoogleSignIn darkMode={darkMode} onSignedIn={onSignedIn} />
                <p className="auth-switch">
                  {t(mode === 'login' ? 'newHere' : 'haveAccount')}{' '}
                  <button
                    type="button"
                    className="text-button"
                    disabled={busy}
                    onClick={() =>
                      changeMode(mode === 'login' ? 'register' : 'login')
                    }
                  >
                    {t(mode === 'login' ? 'createAccount' : 'logIn')}
                  </button>
                </p>
              </>
            )}
            {(mode === 'reset' || mode === 'new-password') && (
              <button
                type="button"
                className="text-button back-link"
                disabled={busy}
                onClick={() => changeMode('login')}
              >
                ← {t('backToLogin')}
              </button>
            )}
          </div>
          <footer className="auth-footnote">{t('footnote')}</footer>
        </section>
      </div>
    </main>
  );
}
