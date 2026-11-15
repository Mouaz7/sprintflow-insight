import {
  createContext,
  useContext,
  useEffect,
  useId,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import type { Language, Project, WorkspaceView } from '../../api/contracts';
import { localizeMessage } from '../i18n/messages';
import { PageGuide } from './Guidance';

export const EditorProjectContext = createContext<
  Pick<Project, 'id' | 'name'> | undefined
>(undefined);

const ConfirmContext = createContext<(message: string) => Promise<boolean>>(
  async (message) => window.confirm(message),
);
export const useConfirm = () => useContext(ConfirmContext);

// Hide and disable the background at every level, including nested dialogs.
function inertBackground(node: HTMLElement | null | undefined): HTMLElement[] {
  const background: HTMLElement[] = [];
  let branch: HTMLElement | null = node?.parentElement ?? null;
  while (branch?.parentElement) {
    for (const sibling of branch.parentElement.children) {
      if (
        sibling !== branch &&
        sibling instanceof HTMLElement &&
        !sibling.inert
      ) {
        sibling.inert = true;
        background.push(sibling);
      }
    }
    if (branch.parentElement === document.body) break;
    branch = branch.parentElement;
  }
  return background;
}

export function useModalFocus(
  close: () => void,
  root: React.RefObject<HTMLDivElement | null>,
) {
  const latestClose = useRef(close);
  useEffect(() => {
    latestClose.current = close;
  });
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const node = root.current;
    const background = inertBackground(node);
    const focusable = () =>
      [
        ...(node?.querySelectorAll<HTMLElement>(
          'button:not(:disabled),input:not(:disabled),select:not(:disabled),textarea:not(:disabled),summary,a[href],[tabindex="0"]',
        ) ?? []),
      ].filter((el) => !el.closest('[hidden]'));
    (focusable()[0] ?? node)?.focus();
    const keydown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        event.stopImmediatePropagation();
        latestClose.current();
      }
      if (event.key === 'Tab') {
        const items = focusable();
        const first = items[0];
        const last = items.at(-1);
        if (!items.length) {
          event.preventDefault();
          node?.focus();
        } else if (
          event.shiftKey &&
          (document.activeElement === first ||
            !node?.contains(document.activeElement))
        ) {
          event.preventDefault();
          last?.focus();
        } else if (
          !event.shiftKey &&
          (document.activeElement === last ||
            !node?.contains(document.activeElement))
        ) {
          event.preventDefault();
          first?.focus();
        }
      }
    };
    node?.addEventListener('keydown', keydown);
    return () => {
      node?.removeEventListener('keydown', keydown);
      background.forEach((element) => {
        element.inert = false;
      });
      document.body.style.overflow = previousOverflow;
      if (previous?.isConnected) previous.focus();
    };
  }, [root]);
}

export function ConfirmProvider({
  children,
  language = 'EN',
}: {
  children: ReactNode;
  language?: Language | undefined;
}) {
  const [request, setRequest] = useState<{
    message: string;
    resolve: (value: boolean) => void;
  }>();
  const pending = useRef<typeof request>(undefined);
  const confirm = (message: string) =>
    new Promise<boolean>((resolve) => {
      pending.current?.resolve(false);
      const next = { message, resolve };
      pending.current = next;
      setRequest(next);
    });
  useEffect(
    () => () => {
      pending.current?.resolve(false);
    },
    [],
  );
  const finish = (value: boolean) => {
    pending.current?.resolve(value);
    pending.current = undefined;
    setRequest(undefined);
  };
  return (
    <ConfirmContext.Provider value={confirm}>
      {children}
      {request && (
        <Confirmation
          message={request.message}
          onFinish={finish}
          language={language}
        />
      )}
    </ConfirmContext.Provider>
  );
}

function Confirmation({
  message,
  onFinish,
  language,
}: {
  message: string;
  onFinish: (value: boolean) => void;
  language: Language;
}) {
  const root = useRef<HTMLDivElement>(null);
  const titleId = useId();
  const descriptionId = useId();
  const sv = language === 'SV';
  useModalFocus(() => onFinish(false), root);
  return (
    <div
      data-theme={
        localStorage.getItem('sprintflow-theme') === 'dark' ? 'dark' : 'light'
      }
      role="presentation"
      className="modal-backdrop confirmation-backdrop"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onFinish(false);
      }}
    >
      <div
        ref={root}
        className="confirmation-card"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={descriptionId}
        tabIndex={-1}
      >
        <span className="eyebrow">{sv ? 'BEKRÄFTELSE' : 'CONFIRMATION'}</span>
        <h2 id={titleId}>
          {sv ? 'Vill du fortsätta?' : 'Do you want to continue?'}
        </h2>
        <p id={descriptionId}>{message}</p>
        <div className="form-actions">
          <button
            type="button"
            className="secondary-action"
            onClick={() => onFinish(false)}
          >
            {sv ? 'Avbryt' : 'Cancel'}
          </button>
          <button
            type="button"
            className="danger-button"
            onClick={() => onFinish(true)}
          >
            {sv ? 'Bekräfta' : 'Confirm'}
          </button>
        </div>
      </div>
    </div>
  );
}

export function Drawer({
  title,
  children,
  onClose,
  dirty = false,
  busy = false,
  language = 'EN',
  projectSelection,
}: {
  title: string;
  children: ReactNode;
  onClose: () => void;
  dirty?: boolean | undefined;
  busy?: boolean | undefined;
  language?: Language | undefined;
  projectSelection?:
    | {
        projects: Project[];
        value?: number | undefined;
        disabled: boolean;
        onChange: (id: number) => void;
      }
    | undefined;
}) {
  const root = useRef<HTMLDivElement>(null);
  const titleId = useId();
  const confirm = useConfirm();
  const sv = language === 'SV';
  const project = useContext(EditorProjectContext);
  const projectHelpId = useId();
  const closing = useRef(false);
  async function close() {
    if (busy || closing.current) return;
    closing.current = true;
    try {
      if (
        !dirty ||
        (await confirm(
          sv
            ? 'Du har osparade ändringar. Vill du kasta dem och stänga?'
            : 'You have unsaved changes. Discard them and close?',
        ))
      )
        onClose();
    } finally {
      closing.current = false;
    }
  }
  useModalFocus(close, root);
  return (
    <div
      role="presentation"
      className="modal-backdrop drawer-backdrop"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) void close();
      }}
    >
      <div
        ref={root}
        className="editor-drawer"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        aria-busy={busy}
      >
        <header>
          <div>
            <span className="eyebrow">
              {sv ? 'ARBETSUTRYMME' : 'WORKSPACE EDITOR'}
            </span>
            <h2 id={titleId}>{title}</h2>
          </div>
          <button
            className="icon-button"
            type="button"
            aria-label={sv ? 'Stäng panel' : 'Close panel'}
            disabled={busy}
            onClick={close}
          >
            ✕
          </button>
        </header>
        <div className="drawer-body">
          {projectSelection ? (
            <label className="editor-project">
              <span>{sv ? 'Projekt' : 'Project'}</span>
              <select
                aria-label={sv ? 'Projekt' : 'Project'}
                aria-describedby={projectHelpId}
                value={
                  projectSelection.projects.some(
                    (item) => item.id === projectSelection.value,
                  )
                    ? projectSelection.value
                    : ''
                }
                disabled={projectSelection.disabled}
                onChange={(event) =>
                  projectSelection.onChange(Number(event.target.value))
                }
              >
                <option value="" disabled>
                  {sv ? 'Välj projekt' : 'Choose project'}
                </option>
                {projectSelection.projects.map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.name}
                  </option>
                ))}
              </select>
              <small id={projectHelpId}>
                {sv
                  ? 'Här sparas det du skapar. Välj projekt innan du väljer sprint eller berättelse.'
                  : 'New work is saved here. Choose the project before selecting a sprint or story.'}
              </small>
            </label>
          ) : (
            project && (
              <p className="editor-project">
                <span>{sv ? 'Projekt' : 'Project'}:</span>
                <strong>{project.name}</strong>
              </p>
            )
          )}
          {children}
        </div>
      </div>
    </div>
  );
}

export function PageHeader({
  eyebrow,
  title,
  description,
  action,
  guide,
  language = 'EN',
}: {
  eyebrow: string;
  title: string;
  description?: string | undefined;
  action?: ReactNode | undefined;
  guide?: WorkspaceView | undefined;
  language?: Language | undefined;
}) {
  return (
    <header className="page-heading">
      <div>
        <p className="eyebrow">{eyebrow}</p>
        <h1>{title}</h1>
        {description && <p className="page-lede">{description}</p>}
        {guide && <PageGuide view={guide} language={language} />}
      </div>
      {action && <div className="page-heading-action">{action}</div>}
    </header>
  );
}

export function Notice({
  error,
  success,
  language = 'EN',
}: {
  error?: string | undefined;
  success?: string | undefined;
  language?: Language | undefined;
}) {
  return error ? (
    <p className="form-message error" role="alert">
      {localizeMessage(error, language)}
    </p>
  ) : success ? (
    <p className="form-message success" role="status">
      {success}
    </p>
  ) : null;
}
