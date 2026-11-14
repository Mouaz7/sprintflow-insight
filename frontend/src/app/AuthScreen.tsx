import { useEffect, useState, type FormEvent } from 'react';
import { api, ApiError, clearCsrfToken } from '../api/client';
import type { Language, User } from '../api/contracts';
import { fieldExample } from './Guidance';
import { InterfaceIcon } from './InterfaceIcons';
import { Notice } from './ui';
import { LanguageSelector } from './LanguageSelector';
import { GoogleSignIn } from './GoogleSignIn';

type AuthMode = 'login' | 'register' | 'reset' | 'new-password';
export function AuthScreen({
  onSignedIn,
  initialError,
}: {
  onSignedIn: (user: User) => Promise<void>;
  initialError: string;
}) {
  const [mode, setMode] = useState<AuthMode>(() =>
    new URLSearchParams(window.location.search).has('resetToken')
      ? 'new-password'
      : 'login',
  );
  const [displayName, setDisplayName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [resetToken] = useState(
    () => new URLSearchParams(window.location.search).get('resetToken') ?? '',
  );
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('');
  const [error, setError] = useState(initialError);
  const [darkMode, setDarkMode] = useState(
    () => localStorage.getItem('sprintflow-theme') === 'dark',
  );
  const [language, setLanguage] = useState<Language>(() =>
    localStorage.getItem('sprintflow-language') === 'SV' ? 'SV' : 'EN',
  );
  const sv = language === 'SV';
  useEffect(() => {
    localStorage.setItem('sprintflow-theme', darkMode ? 'dark' : 'light');
    document.documentElement.dataset.theme = darkMode ? 'dark' : 'light';
    document
      .querySelector('meta[name="theme-color"]')
      ?.setAttribute('content', darkMode ? '#151D2A' : '#FAF8F5');
  }, [darkMode]);
  useEffect(() => {
    localStorage.setItem('sprintflow-language', language);
    document.documentElement.lang = sv ? 'sv' : 'en';
  }, [language, sv]);
  function changeMode(next: AuthMode) {
    if (busy) return;
    if (mode === 'new-password') {
      const url = new URL(window.location.href);
      url.searchParams.delete('resetToken');
      window.history.replaceState(
        null,
        '',
        url.pathname + url.search + url.hash,
      );
    }
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
      setError(
        sv ? 'Lösenorden matchar inte.' : 'Your new passwords do not match.',
      );
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
        setNotice(
          sv
            ? 'Lösenordet har uppdaterats. Du kan nu logga in.'
            : 'Password updated. You can now log in.',
        );
      } else if (mode === 'reset') {
        const response = await api<{ message: string }>(
          '/auth/password/reset/request',
          { method: 'POST', body: JSON.stringify({ email: email.trim() }) },
        );
        setNotice(
          sv
            ? 'Om adressen tillhör ett konto skickas en återställningslänk. Kontrollera även skräpposten.'
            : response.message,
        );
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
            : sv
              ? 'Det gick inte att slutföra begäran. Försök igen.'
              : 'We couldn’t complete that request. Please try again.',
      );
    } finally {
      setBusy(false);
    }
  }
  const title = sv
    ? {
        login: 'Välkommen tillbaka.',
        register: 'Skapa ditt konto.',
        reset: 'Återställ lösenordet.',
        'new-password': 'Välj ett nytt lösenord.',
      }[mode]
    : {
        login: 'Welcome back.',
        register: 'Create your account.',
        reset: 'Reset your password.',
        'new-password': 'Choose a new password.',
      }[mode];
  const subtitle = sv
    ? {
        login: 'Logga in och fortsätt med ditt arbete.',
        register: 'En gemensam plats för ditt team och nästa steg.',
        reset: 'Vi skickar en säker återställningslänk till din e-post.',
        'new-password': 'Ange ditt nya lösenord två gånger.',
      }[mode]
    : {
        login: 'Sign in to continue your work.',
        register: 'A shared space for your team and its next steps.',
        reset: 'We will send a secure reset link to your inbox.',
        'new-password': 'Enter your new password twice.',
      }[mode];
  return (
    <main className="auth-page" data-theme={darkMode ? 'dark' : 'light'}>
      <div className="auth-frame">
        <aside className="auth-story">
          <div className="auth-story-heading">
            <span className="eyebrow">SPRINTFLOW / WORKSPACE</span>
            <h1>
              {sv
                ? 'En plan. Ett team. Framåt.'
                : 'One plan. One team. Forward.'}
            </h1>
            <p>
              {sv
                ? 'Samla idéerna, planera nästa sprint och se arbetet ta form.'
                : 'Bring ideas together, plan your next sprint and see the work take shape.'}
            </p>
          </div>
          <div
            className="auth-example"
            aria-label={sv ? 'Exempel på en arbetsyta' : 'Example workspace'}
          >
            <header>
              <span className="status-badge">{sv ? 'EXEMPEL' : 'EXAMPLE'}</span>
              <span>01 / 03</span>
            </header>
            <h2>{sv ? 'Nästa lansering' : 'The next release'}</h2>
            <p>
              {sv
                ? 'En tydlig väg från idé till leverans.'
                : 'A clear path from idea to delivery.'}
            </p>
            <div className="example-progress">
              <span />
            </div>
            <div className="example-progress-label">
              <strong>68%</strong>
              <span>{sv ? 'av planen klart' : 'of the plan complete'}</span>
            </div>
            <div className="example-task">
              <span className="example-check">✓</span>
              <div>
                <strong>
                  {sv ? 'Sätt ett gemensamt mål' : 'Set a shared goal'}
                </strong>
                <small>
                  {sv ? 'Klart · Produktteam' : 'Done · Product team'}
                </small>
              </div>
            </div>
            <div className="example-task">
              <span className="example-dot" />
              <div>
                <strong>
                  {sv ? 'Bygg nästa upplevelse' : 'Build the next experience'}
                </strong>
                <small>
                  {sv ? 'Pågår · Sprint 04' : 'In progress · Sprint 04'}
                </small>
              </div>
            </div>
          </div>
          <footer>
            <span>01</span>
            <p>
              {sv
                ? 'Fokusera på det viktiga. Håll resten samlat.'
                : 'Focus on what matters. Keep everything connected.'}
            </p>
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
                aria-label={
                  sv
                    ? darkMode
                      ? 'Byt till ljust läge'
                      : 'Byt till mörkt läge'
                    : darkMode
                      ? 'Switch to light mode'
                      : 'Switch to dark mode'
                }
                onClick={() => setDarkMode((value) => !value)}
              >
                <InterfaceIcon name={darkMode ? 'sun' : 'moon'} />
              </button>
            </div>
          </header>
          <div className="auth-card">
            <span className="eyebrow">
              {mode === 'reset' || mode === 'new-password'
                ? sv
                  ? 'KONTOÅTERSTÄLLNING'
                  : 'ACCOUNT RECOVERY'
                : sv
                  ? 'DIN ARBETSYTA'
                  : 'YOUR WORKSPACE'}
            </span>
            <h2>{title}</h2>
            <p className="auth-subtitle">{subtitle}</p>
            <form onSubmit={submit} aria-busy={busy}>
              <fieldset disabled={busy}>
                {mode === 'register' && (
                  <label>
                    {sv ? 'Namn' : 'Name'}
                    <input
                      required
                      maxLength={80}
                      autoComplete="name"
                      value={displayName}
                      onChange={(e) => setDisplayName(e.target.value)}
                      placeholder={fieldExample('name', language)}
                    />
                  </label>
                )}
                {mode !== 'new-password' && (
                  <label>
                    {sv ? 'E-post' : 'Email'}
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
                    {sv ? 'Lösenord' : 'Password'}
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
                      placeholder={
                        mode === 'login'
                          ? sv ? 'Ditt befintliga lösenord' : 'Your existing password'
                          : sv ? 'Välj ett eget lösenord, minst 6 tecken' : 'Choose your own password, at least 6 characters'
                      }
                    />
                  </label>
                )}
                {mode === 'new-password' && (
                  <label>
                    {sv ? 'Bekräfta nytt lösenord' : 'Confirm new password'}
                    <input
                      required
                      minLength={6}
                      maxLength={72}
                      type="password"
                      autoComplete="new-password"
                      value={confirmPassword}
                      placeholder={sv ? 'Skriv samma nya lösenord igen' : 'Enter the same new password again'}
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
                  {sv ? 'Glömt lösenord?' : 'Forgot password?'}
                </button>
              )}
              <Notice language={language} error={error} success={notice} />
              <button className="primary-action" disabled={busy}>
                {busy
                  ? sv
                    ? 'Vänta…'
                    : 'Please wait…'
                  : mode === 'login'
                    ? sv
                      ? 'Logga in'
                      : 'Log in'
                    : mode === 'register'
                      ? sv
                        ? 'Skapa konto'
                        : 'Create account'
                      : mode === 'new-password'
                        ? sv
                          ? 'Spara nytt lösenord'
                          : 'Save new password'
                        : sv
                          ? 'Skicka återställningslänk'
                          : 'Send reset link'}
              </button>
            </form>
            {mode !== 'reset' && mode !== 'new-password' && (
              <>
                <div className="divider">
                  <span>{sv ? 'eller' : 'or'}</span>
                </div>
                <GoogleSignIn
                  language={language}
                  darkMode={darkMode}
                  onSignedIn={onSignedIn}
                />
                <p className="auth-switch">
                  {mode === 'login'
                    ? sv
                      ? 'Ny här?'
                      : 'New here?'
                    : sv
                      ? 'Har du redan ett konto?'
                      : 'Already have an account?'}{' '}
                  <button
                    type="button"
                    className="text-button"
                    disabled={busy}
                    onClick={() =>
                      changeMode(mode === 'login' ? 'register' : 'login')
                    }
                  >
                    {mode === 'login'
                      ? sv
                        ? 'Skapa konto'
                        : 'Create account'
                      : sv
                        ? 'Logga in'
                        : 'Log in'}
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
                ← {sv ? 'Tillbaka till inloggning' : 'Back to log in'}
              </button>
            )}
          </div>
          <footer className="auth-footnote">
            {sv
              ? 'Planera tillsammans. Leverera med tydlighet.'
              : 'Plan together. Deliver with clarity.'}
          </footer>
        </section>
      </div>
    </main>
  );
}
