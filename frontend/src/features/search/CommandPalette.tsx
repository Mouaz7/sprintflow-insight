import { useEffect, useId, useRef, useState } from 'react';
import type { Language, WorkspaceView } from '../../api/contracts';
import { localizeMessage } from '../../shared/i18n/messages';
import { useModalFocus } from '../../shared/ui/ui';
import {
  MAX_QUERY_LENGTH,
  taskByKey,
  taskKeyOf,
  type SearchHit,
  type SearchSegment,
} from './searchApi';
import type { SearchText } from './searchText';
import { GO_TO } from './shortcuts';
import { useWorkspaceSearch } from './useWorkspaceSearch';

/** Where the workspace should go after a palette choice. */
export type SearchTarget = {
  view: WorkspaceView;
  projectId?: number;
  /** Narrows the task board to this task, e.g. SF-12. */
  taskKey?: string;
};

type Command = {
  id: string;
  label: (typeof GO_TO)[number]['label'] | 'newTask' | 'showShortcuts';
  run: () => void;
};

type Option =
  | { kind: 'hit'; id: string; hit: SearchHit }
  | { kind: 'command'; id: string; command: Command }
  | { kind: 'more'; id: string };

type Props = {
  canCreateTask: boolean;
  language: Language;
  t: SearchText;
  onNavigate: (target: SearchTarget) => void;
  onNewTask: () => void;
  onShowShortcuts: () => void;
  onClose: () => void;
};

export function targetOf(hit: SearchHit): SearchTarget {
  switch (hit.type) {
    case 'task':
    case 'comment':
      return hit.key
        ? { view: 'tasks', projectId: hit.projectId, taskKey: hit.key }
        : { view: 'tasks', projectId: hit.projectId };
    case 'epic':
    case 'story':
      return { view: 'backlog', projectId: hit.projectId };
    case 'sprint':
      return { view: 'sprints', projectId: hit.projectId };
    case 'project':
      return { view: 'overview', projectId: hit.projectId };
  }
}

function Highlighted({ segments }: { segments: SearchSegment[] }) {
  return segments.map((segment, index) =>
    segment.match ? (
      <mark key={index}>{segment.text}</mark>
    ) : (
      <span key={index}>{segment.text}</span>
    ),
  );
}

/**
 * Ctrl/⌘+K palette: a modal dialog containing an ARIA 1.2 combobox whose
 * listbox holds search hits and commands. Focus stays in the input; the active
 * option is announced through aria-activedescendant.
 */
export function CommandPalette({
  canCreateTask,
  language,
  t,
  onNavigate,
  onNewTask,
  onShowShortcuts,
  onClose,
}: Props) {
  const ref = useRef<HTMLDivElement>(null);
  const titleId = useId();
  const listId = useId();
  const optionPrefix = useId();
  const [text, setText] = useState('');
  const [active, setActive] = useState(0);
  const [keyError, setKeyError] = useState('');
  useModalFocus(onClose, ref);
  const search = useWorkspaceSearch(text);

  const commands: Command[] = [
    ...GO_TO.map((item) => ({
      id: `go-${item.view}`,
      label: item.label,
      run: () => onNavigate({ view: item.view }),
    })),
    ...(canCreateTask
      ? [{ id: 'new-task', label: 'newTask', run: onNewTask } as const]
      : []),
    { id: 'shortcuts', label: 'showShortcuts', run: onShowShortcuts },
  ];
  const needle = search.query.toLocaleLowerCase();
  const matchingCommands = needle
    ? commands.filter((command) =>
        t(command.label).toLocaleLowerCase().includes(needle),
      )
    : commands;
  const options: Option[] = [
    ...search.items.map((hit): Option => ({
      kind: 'hit',
      id: `${optionPrefix}-${hit.type}-${hit.id}`,
      hit,
    })),
    ...(search.nextCursor
      ? [{ kind: 'more', id: `${optionPrefix}-more` } as const]
      : []),
    ...matchingCommands.map((command): Option => ({
      kind: 'command',
      id: `${optionPrefix}-${command.id}`,
      command,
    })),
  ];
  const activeIndex = Math.min(active, options.length - 1);
  const activeOption = activeIndex >= 0 ? options[activeIndex] : undefined;

  const activeId = activeOption?.id;
  useEffect(() => {
    if (activeId)
      document
        .getElementById(activeId)
        ?.scrollIntoView?.({ block: 'nearest', inline: 'nearest' });
  }, [activeId]);

  function choose(option: Option) {
    if (option.kind === 'hit') onNavigate(targetOf(option.hit));
    else if (option.kind === 'command') option.command.run();
    else search.loadMore();
  }

  async function jumpToKey(key: string) {
    try {
      const task = await taskByKey(key);
      onNavigate({
        view: 'tasks',
        projectId: task.projectId,
        taskKey: task.key,
      });
    } catch {
      setKeyError(t('notFound', { key }));
    }
  }

  function keydown(event: React.KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      if (!options.length) return;
      const step = event.key === 'ArrowDown' ? 1 : -1;
      setActive((activeIndex + step + options.length) % options.length);
    } else if (event.key === 'Home' && event.ctrlKey) {
      event.preventDefault();
      setActive(0);
    } else if (event.key === 'End' && event.ctrlKey) {
      event.preventDefault();
      setActive(Math.max(options.length - 1, 0));
    } else if (event.key === 'Enter') {
      event.preventDefault();
      const key = taskKeyOf(text);
      // A task key jumps straight to the task, also before results arrive.
      // Once hits are shown, the exact match is the first (active) option.
      if (key && !search.items.length) void jumpToKey(key);
      else if (activeOption) choose(activeOption);
    }
  }

  const status = search.loading
    ? t('loading')
    : search.error
      ? localizeMessage(search.error, language)
      : search.query && !search.items.length
        ? t('noResults')
        : search.query
          ? t('resultCount', { count: search.items.length })
          : '';
  const firstCommand = options.findIndex((option) => option.kind === 'command');

  return (
    <div
      role="presentation"
      className="modal-backdrop command-backdrop"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <div
        ref={ref}
        className="command-palette"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        data-command-palette=""
        tabIndex={-1}
      >
        <h2 id={titleId} className="sr-only">
          {t('title')}
        </h2>
        <input
          className="command-input"
          type="text"
          role="combobox"
          aria-label={t('inputLabel')}
          aria-expanded={options.length > 0}
          aria-controls={listId}
          aria-autocomplete="list"
          aria-activedescendant={activeOption?.id}
          placeholder={t('placeholder')}
          maxLength={MAX_QUERY_LENGTH}
          autoComplete="off"
          spellCheck={false}
          value={text}
          onChange={(event) => {
            setText(event.target.value);
            setActive(0);
            setKeyError('');
          }}
          onKeyDown={keydown}
        />
        <p className="command-status" role="status">
          {keyError || status}
        </p>
        <ul
          id={listId}
          role="listbox"
          aria-label={t('results')}
          className="command-list"
        >
          {options.map((option, index) => (
            // Keyboard selection lives on the combobox input (aria-activedescendant).
            // eslint-disable-next-line jsx-a11y/click-events-have-key-events
            <li
              key={option.id}
              id={option.id}
              role="option"
              aria-selected={index === activeIndex}
              className={
                index === firstCommand && index > 0
                  ? 'command-option command-first'
                  : 'command-option'
              }
              onMouseDown={(event) => event.preventDefault()}
              onMouseMove={() => {
                if (index !== activeIndex) setActive(index);
              }}
              onClick={() => choose(option)}
            >
              {option.kind === 'hit' ? (
                <HitRow hit={option.hit} t={t} />
              ) : option.kind === 'more' ? (
                <span className="command-label">{t('loadMore')}</span>
              ) : (
                <span className="command-label">{t(option.command.label)}</span>
              )}
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}

function HitRow({ hit, t }: { hit: SearchHit; t: SearchText }) {
  return (
    <span className="command-hit">
      <span className="command-meta">
        <span className={`command-type command-type-${hit.type}`}>
          {t(hit.type)}
        </span>
        {hit.key && <span className="task-key">{hit.key}</span>}
        {hit.exactMatch && (
          <span className="command-exact">{t('exactMatch')}</span>
        )}
      </span>
      <span className="command-title">
        <Highlighted segments={hit.titleSegments} />
      </span>
      {hit.snippet.length > 0 && (
        <span className="command-snippet">
          <Highlighted segments={hit.snippet} />
        </span>
      )}
      <span className="command-project">
        {t('inProject', { project: hit.projectName })}
      </span>
    </span>
  );
}
