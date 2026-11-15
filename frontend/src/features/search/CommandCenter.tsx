import './search.css';
import { useState } from 'react';
import type { Language, WorkspaceView } from '../../api/contracts';
import { CommandPalette, type SearchTarget } from './CommandPalette';
import { ShortcutHelp } from './ShortcutHelp';
import { searchText } from './searchText';
import { useGlobalShortcuts } from './shortcuts';

export type { SearchTarget } from './CommandPalette';

type Props = {
  language: Language;
  onNavigate: (target: SearchTarget) => void;
  onNewTask: () => void;
};

/**
 * The workspace's single mount point for search: a header button, the Ctrl/⌘+K
 * command palette, the shortcut help dialog and the global keyboard listener.
 */
export function CommandCenter({ language, onNavigate, onNewTask }: Props) {
  const [open, setOpen] = useState<'palette' | 'help' | null>(null);
  const t = searchText(language);
  const close = () => setOpen(null);
  useGlobalShortcuts({
    togglePalette: () =>
      setOpen((current) => (current === 'palette' ? null : 'palette')),
    openPalette: () => setOpen('palette'),
    openHelp: () => setOpen('help'),
    newTask: onNewTask,
    goTo: (view: WorkspaceView) => onNavigate({ view }),
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
      {open === 'palette' && (
        <CommandPalette
          language={language}
          t={t}
          onClose={close}
          onNavigate={(target) => {
            close();
            onNavigate(target);
          }}
          onNewTask={() => {
            close();
            onNewTask();
          }}
          onShowShortcuts={() => setOpen('help')}
        />
      )}
      {open === 'help' && <ShortcutHelp t={t} onClose={close} />}
    </>
  );
}
