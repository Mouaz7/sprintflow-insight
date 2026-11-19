import { useState } from 'react';
import { ApiError } from '../../api/client';
import { useT } from '../../shared/i18n/LanguageProvider';
import { Drawer, Notice } from '../../shared/ui/ui';
import { defaultWidgets, widgetTypes, type DashboardLayout } from './contracts';
import { dashboardText } from './i18n';
import { useSaveDashboard } from './queries';
export function DashboardEditor({
  projectId,
  initial,
  onClose,
  onSaved,
}: {
  projectId: number;
  initial: DashboardLayout;
  onClose: () => void;
  onSaved: () => void;
}) {
  const t = useT(dashboardText);
  const [draft, setDraft] = useState(() => structuredClone(initial));
  const save = useSaveDashboard(projectId);
  const conflict =
    save.error instanceof ApiError && [412, 428].includes(save.error.status);
  const dirty =
    JSON.stringify(draft.widgets) !== JSON.stringify(initial.widgets);
  function move(index: number, offset: number) {
    setDraft((old) => {
      const widgets = [...old.widgets];
      const item = widgets.splice(index, 1)[0];
      if (item) widgets.splice(index + offset, 0, item);
      return { ...old, widgets };
    });
  }
  return (
    <Drawer
      title={t('edit')}
      onClose={onClose}
      dirty={dirty}
      busy={save.isPending}
    >
      <form
        className="drawer-body dashboard-editor"
        onSubmit={(e) => {
          e.preventDefault();
          save.mutate(draft, {
            onSuccess: () => {
              onSaved();
              onClose();
            },
          });
        }}
      >
        <p>{t('editorHint')}</p>
        {save.error && (
          <Notice error={t(conflict ? 'conflict' : 'saveFailed')} />
        )}
        <fieldset>
          <legend>{t('edit')}</legend>
          {widgetTypes.map((type) => (
            <label key={type}>
              <input
                type="checkbox"
                checked={draft.widgets.some((w) => w.type === type)}
                onChange={(e) =>
                  setDraft((old) => ({
                    ...old,
                    widgets: e.target.checked
                      ? [...old.widgets, { type, wide: false }]
                      : old.widgets.filter((w) => w.type !== type),
                  }))
                }
              />
              {t(type)}
            </label>
          ))}
        </fieldset>
        <ol className="dashboard-order">
          {draft.widgets.map((widget, index) => (
            <li key={widget.type}>
              <strong>{t(widget.type)}</strong>
              <label>
                <input
                  type="checkbox"
                  checked={widget.wide}
                  onChange={(e) =>
                    setDraft((old) => ({
                      ...old,
                      widgets: old.widgets.map((w) =>
                        w.type === widget.type
                          ? { ...w, wide: e.target.checked }
                          : w,
                      ),
                    }))
                  }
                />
                {t('wide')}
              </label>
              <div className="dashboard-actions">
                <button
                  type="button"
                  className="secondary-action"
                  disabled={index === 0}
                  aria-label={`${t('up')}: ${t(widget.type)}`}
                  onClick={() => move(index, -1)}
                >
                  {t('up')}
                </button>
                <button
                  type="button"
                  className="secondary-action"
                  disabled={index === draft.widgets.length - 1}
                  aria-label={`${t('down')}: ${t(widget.type)}`}
                  onClick={() => move(index, 1)}
                >
                  {t('down')}
                </button>
              </div>
            </li>
          ))}
        </ol>
        <button
          type="button"
          className="secondary-action"
          onClick={() =>
            setDraft((old) => ({ ...old, widgets: defaultWidgets() }))
          }
        >
          {t('reset')}
        </button>
        <div className="editor-footer">
          <button
            className="primary-action"
            disabled={save.isPending || conflict}
          >
            {t(save.isPending ? 'saving' : 'save')}
          </button>
          <button type="button" className="secondary-action" onClick={onClose}>
            {t('cancel')}
          </button>
        </div>
      </form>
    </Drawer>
  );
}
