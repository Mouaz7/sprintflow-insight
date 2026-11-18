import { useEffect, useRef } from 'react';
import type { WorkspaceView } from '../../api/contracts';
import type { SearchTextKey } from './searchText';

/** `g` followed by one of these keys navigates; the second key must follow within a second. */
export const GO_TO = [
  { key: 'o', view: 'overview', label: 'goOverview' },
  { key: 'b', view: 'tasks', label: 'goBoard' },
  { key: 'l', view: 'backlog', label: 'goBacklog' },
  { key: 's', view: 'sprints', label: 'goSprints' },
  { key: 'r', view: 'roadmap', label: 'goRoadmap' },
  { key: 'm', view: 'metrics', label: 'goMetrics' },
  { key: 'p', view: 'projects', label: 'goProjects' },
] as const satisfies readonly {
  key: string;
  view: WorkspaceView;
  label: SearchTextKey;
}[];

export const SEQUENCE_TIMEOUT_MS = 1000;

export type ShortcutHandlers = {
  dismiss: () => void;
  togglePalette: () => void;
  openPalette: () => void;
  openHelp: () => void;
  newTask: () => void;
  goTo: (view: WorkspaceView) => void;
};

function typing(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  return (
    target.isContentEditable ||
    ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName)
  );
}

function modalOpen(): boolean {
  return document.querySelector('[aria-modal="true"]') !== null;
}

/**
 * The single global keyboard listener: Ctrl/⌘+K, `/`, `?`, `c` and `g` + key.
 * Plain keys are ignored while typing in a field or while any modal dialog is
 * open, so they never steal text input or act behind a dialog.
 */
export function useGlobalShortcuts(handlers: ShortcutHandlers) {
  const latest = useRef(handlers);
  useEffect(() => {
    latest.current = handlers;
  });
  useEffect(() => {
    let pendingG = 0;
    const keydown = (event: KeyboardEvent) => {
      if (event.defaultPrevented || event.isComposing) return;
      const key = event.key.toLowerCase();
      if (event.key === 'Escape') {
        latest.current.dismiss();
        return;
      }
      if ((event.ctrlKey || event.metaKey) && !event.altKey && key === 'k') {
        if (modalOpen() && !document.querySelector('[data-command-palette]'))
          return;
        event.preventDefault();
        latest.current.togglePalette();
        return;
      }
      if (event.ctrlKey || event.metaKey || event.altKey) return;
      if (typing(event.target) || modalOpen()) return;
      if (pendingG && Date.now() - pendingG <= SEQUENCE_TIMEOUT_MS) {
        pendingG = 0;
        const target = GO_TO.find((item) => item.key === key);
        if (target) {
          event.preventDefault();
          latest.current.goTo(target.view);
        }
        return;
      }
      pendingG = 0;
      if (event.key === '?') {
        event.preventDefault();
        latest.current.openHelp();
      } else if (event.key === '/') {
        event.preventDefault();
        latest.current.openPalette();
      } else if (key === 'g' && !event.shiftKey) {
        pendingG = Date.now();
      } else if (key === 'c' && !event.shiftKey) {
        event.preventDefault();
        latest.current.newTask();
      }
    };
    document.addEventListener('keydown', keydown);
    return () => document.removeEventListener('keydown', keydown);
  }, []);
}
