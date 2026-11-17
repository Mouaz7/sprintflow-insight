import { useState } from 'react';
import { api } from '../../api/client';
import type { WorkspaceView } from '../../api/contracts';
import { useT } from '../../shared/i18n/LanguageProvider';
import { Drawer, Notice } from '../../shared/ui/ui';
import { onboardingText } from './i18n';
export function Onboarding({
  onClose,
  onNavigate,
}: {
  onClose: () => void;
  onNavigate: (view: WorkspaceView) => void;
}) {
  const t = useT(onboardingText);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  async function finish(view?: WorkspaceView) {
    if (busy) return;
    setBusy(true);
    try {
      await api('/auth/preferences', {
        method: 'PUT',
        body: JSON.stringify({ onboardingCompleted: true }),
      });
      onClose();
      if (view) onNavigate(view);
    } catch (cause) {
      setError((cause as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <Drawer
      title={t('title')}
      busy={busy}
      onClose={() => {
        void finish();
      }}
    >
      <p>{t('intro')}</p>
      <ol className="onboarding-steps">
        <li>
          <h2>{t('createProject')}</h2>
          <p>{t('createProjectHelp')}</p>
          <button disabled={busy} onClick={() => finish('projects')}>
            {t('openProjects')}
          </button>
        </li>
        <li>
          <h2>{t('plan')}</h2>
          <p>{t('planHelp')}</p>
          <button disabled={busy} onClick={() => finish('sprints')}>
            {t('openSprints')}
          </button>
        </li>
        <li>
          <h2>{t('collaborate')}</h2>
          <p>{t('collaborateHelp')}</p>
          <button disabled={busy} onClick={() => finish('tasks')}>
            {t('openTasks')}
          </button>
        </li>
      </ol>
      <Notice error={error} />
      <button
        className="primary-action"
        disabled={busy}
        onClick={() => finish()}
      >
        {t('ready')}
      </button>
    </Drawer>
  );
}
