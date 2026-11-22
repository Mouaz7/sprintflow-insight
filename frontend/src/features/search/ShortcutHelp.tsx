import { Fragment, useId, useRef } from 'react';
import { useModalFocus } from '../../shared/ui/ui';
import { GO_TO } from './shortcuts';
import type { SearchText } from './searchText';

type Props = {
  t: SearchText;
  onClose: () => void;
  characterShortcuts: boolean;
  onCharacterShortcutsChange: (enabled: boolean) => void;
};

function Keys({ keys, then }: { keys: string[]; then?: string }) {
  return (
    <span className="shortcut-keys">
      {keys.map((key, index) => (
        <Fragment key={key}>
          {index > 0 && <span className="shortcut-then"> {then} </span>}
          <kbd>{key}</kbd>
        </Fragment>
      ))}
    </span>
  );
}

/** Accessible list of keyboard shortcuts: modal dialog with focus trap. */
export function ShortcutHelp({
  t,
  onClose,
  characterShortcuts,
  onCharacterShortcutsChange,
}: Props) {
  const ref = useRef<HTMLDivElement>(null);
  const titleId = useId();
  const introId = useId();
  useModalFocus(onClose, ref);
  const mac =
    typeof navigator !== 'undefined' &&
    /Mac|iPhone|iPad/.test(navigator.platform);
  const general: [string[], string][] = [
    [[mac ? '⌘' : 'Ctrl', 'K'], t('keyPalette')],
    [['/'], t('keySlash')],
    [['?'], t('keyHelp')],
    [['C'], t('keyNewTask')],
  ];
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
        className="shortcut-help"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={introId}
        tabIndex={-1}
      >
        <header>
          <h2 id={titleId}>{t('shortcutsTitle')}</h2>
          <button
            type="button"
            className="icon-button"
            aria-label={t('close')}
            onClick={onClose}
          >
            ✕
          </button>
        </header>
        <p id={introId}>{t('shortcutsIntro')}</p>
        <label className="shortcut-preference">
          <input
            type="checkbox"
            checked={characterShortcuts}
            onChange={(event) =>
              onCharacterShortcutsChange(event.currentTarget.checked)
            }
          />
          {t('characterShortcuts')}
        </label>
        <h3>{t('general')}</h3>
        <dl>
          {general.map(([keys, label]) => (
            <div key={label}>
              <dt>
                <Keys keys={keys} />
              </dt>
              <dd>{label}</dd>
            </div>
          ))}
        </dl>
        <h3>{t('navigation')}</h3>
        <dl>
          {GO_TO.map((item) => (
            <div key={item.key}>
              <dt>
                <Keys
                  keys={['G', item.key.toUpperCase()]}
                  then={t('keyThen')}
                />
              </dt>
              <dd>{t(item.label)}</dd>
            </div>
          ))}
        </dl>
        <h3>{t('paletteKeys')}</h3>
        <dl>
          <div>
            <dt>
              <Keys keys={['↑', '↓']} />
            </dt>
            <dd>{t('keyArrows')}</dd>
          </div>
          <div>
            <dt>
              <Keys keys={['Enter']} />
            </dt>
            <dd>{t('keyEnter')}</dd>
          </div>
        </dl>
      </div>
    </div>
  );
}
