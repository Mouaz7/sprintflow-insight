import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import qrcode from 'qrcode-generator';
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { api, clearCsrfToken } from '../../api/client';
import { meQuery, projectsQuery } from '../../api/queries';
import { useT } from '../../shared/i18n/LanguageProvider';
import { Notice } from '../../shared/ui/ui';
import { profileText } from './i18n';

type Settings = {
  enabled: boolean;
  recoveryCodesRemaining: number;
  requiredByOrganization: boolean;
};
type Setup = { secret: string; uri: string };
const settingsKey = ['account', 'two-factor'] as const;
const path = '/auth/two-factor';

export default function TwoFactorSettings() {
  const t = useT(profileText);
  const client = useQueryClient();
  const status = useQuery({
    queryKey: settingsKey,
    queryFn: () => api<Settings>(path),
  });
  const [password, setPassword] = useState('');
  const [code, setCode] = useState('');
  const [setup, setSetup] = useState<Setup>();
  const [recovery, setRecovery] = useState<string[]>([]);
  const [message, setMessage] = useState('');
  const configured = status.data?.enabled ?? false;

  const submitStep = useMutation({
    gcTime: 0,
    mutationFn: async () => {
      const options = {
        method: 'POST',
        body: JSON.stringify({ password, code }),
      };
      if (configured) {
        await api(`${path}/disable`, options);
        return { kind: 'disabled' } as const;
      }
      if (setup) {
        const result = await api<{ recoveryCodes: string[] }>(
          `${path}/enable`,
          options,
        );
        return { kind: 'enabled', codes: result.recoveryCodes } as const;
      }
      const details = await api<Setup>(`${path}/setup`, {
        method: 'POST',
        body: JSON.stringify({ password }),
      });
      return { kind: 'setup', details } as const;
    },
    onSuccess: async (result) => {
      if (result.kind === 'setup') {
        setSetup(result.details);
        setPassword('');
        return;
      }
      clearCsrfToken();
      setSetup(undefined);
      setPassword('');
      setCode('');
      setRecovery(result.kind === 'enabled' ? result.codes : []);
      setMessage(t(result.kind === 'enabled' ? 'mfaEnabled' : 'mfaDisabled'));
      await Promise.all([
        client.invalidateQueries({ queryKey: settingsKey }),
        client.invalidateQueries({ queryKey: meQuery.queryKey }),
        client.invalidateQueries({ queryKey: projectsQuery.queryKey }),
      ]);
    },
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    setMessage('');
    submitStep.mutate();
  }

  return (
    <section className="profile-card" id="two-factor">
      <h2>{t('mfaTitle')}</h2>
      <Notice
        error={status.error?.message ?? submitStep.error?.message}
        success={message || undefined}
      />
      {status.isPending && <p role="status">{t('mfaLoading')}</p>}
      {status.data?.requiredByOrganization && (
        <p>{t('mfaOrganizationRequired')}</p>
      )}
      {configured && (
        <p>
          {t('mfaRecoveryRemaining', {
            count: status.data?.recoveryCodesRemaining ?? 0,
          })}
        </p>
      )}
      {setup && !configured && (
        <div>
          <p>{t('mfaScan')}</p>
          <AuthenticatorQr uri={setup.uri} label={t('mfaQr')} />
          <label>
            {t('mfaSecret')}
            <input
              readOnly
              type="text"
              value={setup.secret}
              onFocus={(event) => event.target.select()}
            />
          </label>
        </div>
      )}
      {status.isSuccess &&
        !(configured && status.data.requiredByOrganization) && (
          <form className="editor-form" onSubmit={submit}>
            <label>
              {t('mfaPassword')}
              <input
                type="password"
                required
                maxLength={72}
                autoComplete="current-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
            </label>
            {(configured || setup) && (
              <label>
                {t('mfaCode')}
                <input
                  required
                  maxLength={32}
                  autoComplete="one-time-code"
                  value={code}
                  onChange={(event) => setCode(event.target.value)}
                />
              </label>
            )}
            <button className="primary-action" disabled={submitStep.isPending}>
              {t(configured ? 'mfaDisable' : setup ? 'mfaEnable' : 'mfaSetup')}
            </button>
          </form>
        )}
      {!!recovery.length && (
        <div>
          <h3>{t('mfaRecovery')}</h3>
          <p>{t('mfaKeepRecovery')}</p>
          <ul className="mfa-recovery-codes">
            {recovery.map((item) => (
              <li key={item}>
                <code>{item}</code>
              </li>
            ))}
          </ul>
          <button type="button" onClick={() => setRecovery([])}>
            {t('mfaHideCodes')}
          </button>
        </div>
      )}
    </section>
  );
}

function AuthenticatorQr({ uri, label }: { uri: string; label: string }) {
  const canvas = useRef<HTMLCanvasElement>(null);
  useEffect(() => {
    const surface = canvas.current;
    const context = surface?.getContext('2d');
    if (!surface || !context) return;
    const matrix = qrcode(0, 'M');
    matrix.addData(uri, 'Byte');
    matrix.make();
    const count = matrix.getModuleCount();
    const quietZone = 4;
    const cell = 4;
    surface.width = surface.height = (count + 2 * quietZone) * cell;
    context.fillStyle = '#fff';
    context.fillRect(0, 0, surface.width, surface.height);
    context.fillStyle = '#000';
    for (let row = 0; row < count; row++) {
      for (let col = 0; col < count; col++) {
        if (matrix.isDark(row, col))
          context.fillRect(
            (col + quietZone) * cell,
            (row + quietZone) * cell,
            cell,
            cell,
          );
      }
    }
  }, [uri]);
  return (
    <canvas ref={canvas} role="img" aria-label={label} className="mfa-qr" />
  );
}
