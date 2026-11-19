import { lazy, Suspense, useState } from 'react';
import type { WorkspaceView } from '../../api/contracts';
import type { SearchTarget } from './CommandPalette';

import { searchLexicon } from './searchText';
import { useLanguage, useT } from '../../shared/i18n/LanguageProvider';
import { useGlobalShortcuts } from './shortcuts';

const loadPalette = () =>
  import('./CommandPalette').then((module) => ({
    default: module.CommandPalette,
  }));
const CommandPalette = lazy(loadPalette);
const ShortcutHelp = lazy(() =>
  import('./ShortcutHelp').then((module) => ({ default: module.ShortcutHelp })),
);

export type { SearchTarget } from './CommandPalette';

type Props = {
  projectId?: number | undefined;
  canCreateTask: boolean;
  onNavigate: (target: SearchTarget) => void;
  onNewTask: () => void;
};

/**
 * The workspace's single mount point for search: a header button, the Ctrl/⌘+K
 * command palette, the shortcut help dialog and the global keyboard listener.
 */
export function CommandCenter({
  projectId,
  canCreateTask,
  onNavigate,
  onNewTask,
}: Props) {
  const { language } = useLanguage();
  const [open, setOpen] = useState<'palette' | 'help' | null>(null);
  const t = useT(searchLexicon);
  const close = () => setOpen(null);
  useGlobalShortcuts({
    togglePalette: () =>
      setOpen((current) => (current === 'palette' ? null : 'palette')),
    openPalette: () => setOpen('palette'),
    openHelp: () => setOpen('help'),
    newTask: () => {
      close();
      if (canCreateTask) onNewTask();
    },
    dismiss: close,
    goTo: (view: WorkspaceView) => {
      close();
      onNavigate({ view });
    },
  });
  const mac =
    typeof navigator !== 'undefined' &&
    /Mac|iPhone|iPad/.test(navigator.platform);
  return (
    <>
      <button
        type="button"
        className="command-launcher"
        title={t('openHint')}
        aria-keyshortcuts={mac ? 'Meta+K' : 'Control+K'}
        onMouseEnter={() => {
          void loadPalette();
        }}
        onFocus={() => {
          void loadPalette();
        }}
        onClick={() => setOpen('palette')}
      >
        <svg
          width="18"
          height="18"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
          aria-hidden="true"
        >
          <circle cx="11" cy="11" r="7" />
          <path d="m20 20-4-4" />
        </svg>
        <span className="command-launcher-text">{t('open')}</span>
        <kbd aria-hidden="true">{mac ? '⌘K' : 'Ctrl K'}</kbd>
      </button>
      <Suspense
        fallback={
          <p className="sr-only" role="status">
            {t('loading')}
          </p>
        }
      >
        {open === 'palette' && (
          <CommandPalette
            key={projectId}
            projectId={projectId}
            canCreateTask={canCreateTask}
            language={language}
            t={t}
            onClose={close}
            onNavigate={(target) => {
              close();
              onNavigate(target);
            }}
            onNewTask={() => {
              close();
              if (canCreateTask) onNewTask();
            }}
            onShowShortcuts={() => setOpen('help')}
          />
        )}
        {open === 'help' && <ShortcutHelp t={t} onClose={close} />}
      </Suspense>
    </>
  );
}
