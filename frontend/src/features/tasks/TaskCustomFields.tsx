import { useState, type FormEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api, ApiError } from '../../api/client';
import type {
  CustomFieldDefinition,
  ProjectMember,
  Task,
  TaskCustomFields,
} from '../../api/contracts';
import { taskCustomFieldsQuery } from '../../api/customFields';
import { useLanguage, useT } from '../../shared/i18n/LanguageProvider';
import { localizeMessage } from '../../shared/i18n/messages';
import { Drawer, useConfirm } from '../../shared/ui/ui';
import { uiText } from '../../shared/ui/i18n';
import { FormGuide } from '../../shared/ui/Guidance';
import { CustomFieldInput } from '../../shared/ui/custom-fields/CustomFieldInput';
import { customFieldText } from '../../shared/ui/custom-fields/i18n';
import { ErrorState, Skeleton } from '../../shared/ui/States';
import { taskFieldsText } from './customFieldsText';

type Props = {
  task: Task;
  definitions: CustomFieldDefinition[];
  members: ProjectMember[];
  readOnly: boolean;
  onClose: () => void;
  onSaved: () => Promise<void>;
};
const valuesOf = (snapshot: TaskCustomFields) =>
  Object.fromEntries(
    snapshot.values.map((value) => [value.fieldId, value.value]),
  );
const comparable = (values: Record<string, string>) =>
  JSON.stringify(
    Object.entries(values)
      .filter(([, value]) => value !== '')
      .sort(([a], [b]) => a.localeCompare(b)),
  );

export function TaskCustomFieldsDrawer(props: Props) {
  const t = useT(taskFieldsText);
  const query = useQuery(
    taskCustomFieldsQuery(props.task.projectId, props.task.id),
  );
  if (!query.data)
    return (
      <Drawer title={t('title')} onClose={props.onClose}>
        {query.isError ? (
          <ErrorState
            detail={t('failed')}
            onRetry={() => {
              void query.refetch();
            }}
          />
        ) : (
          <Skeleton label={t('loading')} />
        )}
      </Drawer>
    );
  return (
    <ValuesEditor
      {...props}
      snapshot={query.data}
      loadError={query.isError}
      reload={async () => {
        const result = await query.refetch();
        return result.isError ? undefined : result.data;
      }}
    />
  );
}

function ValuesEditor({
  task,
  definitions,
  members,
  readOnly,
  onClose,
  onSaved,
  snapshot,
  loadError,
  reload,
}: Props & {
  snapshot: TaskCustomFields;
  loadError: boolean;
  reload: () => Promise<TaskCustomFields | undefined>;
}) {
  const t = useT(taskFieldsText),
    f = useT(customFieldText),
    ui = useT(uiText);
  const { language } = useLanguage();
  const confirm = useConfirm();
  const [baseline, setBaseline] = useState(snapshot);
  const [draft, setDraft] = useState<Record<string, string>>(() =>
    valuesOf(snapshot),
  );
  const [saving, setSaving] = useState(false),
    [error, setError] = useState('');
  const dirty = comparable(draft) !== comparable(valuesOf(baseline));
  async function cancel() {
    if (!dirty || (await confirm(ui('discardChanges')))) onClose();
  }
  async function reloadValues() {
    if (dirty && !(await confirm(ui('discardChanges')))) return;
    const next = await reload();
    if (next) {
      setBaseline(next);
      setDraft(valuesOf(next));
      setError('');
    }
  }
  async function save(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError('');
    try {
      await api<TaskCustomFields>(`/tasks/${task.id}/custom-fields`, {
        method: 'PUT',
        headers: { 'If-Match': `"${baseline.version}"` },
        body: JSON.stringify({
          values: Object.entries(draft).map(([fieldId, value]) => ({
            fieldId: Number(fieldId),
            value,
          })),
        }),
      });
      await onSaved();
    } catch (reason) {
      setError(
        reason instanceof ApiError && [412, 428].includes(reason.status)
          ? t('conflict')
          : localizeMessage(
              reason instanceof Error ? reason.message : t('saveFailed'),
              language,
            ),
      );
    } finally {
      setSaving(false);
    }
  }
  const current = new Map(
    snapshot.values.map((value) => [value.fieldId, value]),
  );
  return (
    <Drawer
      title={`${t('title')} · ${task.key ?? task.title}`}
      onClose={onClose}
      busy={saving}
      dirty={dirty}
    >
      <FormGuide topic="customFieldValues" />
      {loadError && (
        <p role="alert" className="form-message error">
          {t('failed')}
        </p>
      )}
      {readOnly && <p>{t('readonly')}</p>}
      {definitions.length === 0 ? (
        <p>{t('empty')}</p>
      ) : readOnly && !dirty ? (
        <dl className="custom-fields-grid">
          {definitions.map((field) => (
            <div key={field.id}>
              <dt>{field.name}</dt>
              <dd>{current.get(field.id)?.displayValue ?? f('unset')}</dd>
            </div>
          ))}
        </dl>
      ) : (
        <form className="editor-form" onSubmit={save}>
          <fieldset
            disabled={saving || readOnly}
            className="custom-fields-grid"
          >
            {definitions.map((field) => (
              <CustomFieldInput
                key={field.id}
                field={field}
                value={draft[field.id] ?? ''}
                members={members}
                onChange={(value) =>
                  setDraft((before) => ({ ...before, [field.id]: value }))
                }
              />
            ))}
          </fieldset>
          {snapshot.version !== baseline.version && (
            <p role="status">{t('changed')}</p>
          )}
          {error && (
            <p role="alert" className="form-message error">
              {error}
            </p>
          )}
          <div className="editor-actions">
            <button className="primary-action" disabled={saving || readOnly}>
              {t(saving ? 'saving' : 'save')}
            </button>
            <button
              type="button"
              className="secondary-action"
              disabled={saving}
              onClick={() => {
                void reloadValues();
              }}
            >
              {t('reload')}
            </button>
            <button
              type="button"
              className="secondary-action"
              disabled={saving}
              onClick={() => {
                void cancel();
              }}
            >
              {t('cancel')}
            </button>
          </div>
        </form>
      )}
    </Drawer>
  );
}
