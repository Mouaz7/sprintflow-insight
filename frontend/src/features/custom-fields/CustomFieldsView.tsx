import { useState, type FormEvent } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router';
import { api, ApiError } from '../../api/client';
import type {
  CustomFieldDefinition,
  CustomFieldKind,
  Project,
} from '../../api/contracts';
import { customFieldsQuery } from '../../api/customFields';
import { afterProjectWrite, roleQuery } from '../../api/queries';
import { useLanguage, useT } from '../../shared/i18n/LanguageProvider';
import { localizeMessage } from '../../shared/i18n/messages';
import { customFieldText } from '../../shared/ui/custom-fields/i18n';
import { EmptyState, ErrorState, Skeleton } from '../../shared/ui/States';
import { Drawer, PageHeader, useConfirm } from '../../shared/ui/ui';
import { FormGuide } from '../../shared/ui/Guidance';
import { uiText } from '../../shared/ui/i18n';
import { fieldsText } from './i18n';

const kinds: CustomFieldKind[] = ['TEXT', 'NUMBER', 'DATE', 'SELECT', 'USER'];

export function CustomFieldsView({
  project,
}: {
  project: Project | undefined;
}) {
  const t = useT(fieldsText);
  if (!project)
    return (
      <section className="page custom-fields-page">
        <PageHeader
          guide="fields"
          eyebrow={t('eyebrow')}
          title={t('title')}
          description={t('description')}
        />
        <EmptyState title={t('choose')} />
      </section>
    );
  return <ProjectFields key={project.id} project={project} />;
}

function ProjectFields({ project }: { project: Project }) {
  const t = useT(fieldsText),
    f = useT(customFieldText);
  const { language } = useLanguage();
  const client = useQueryClient();
  const confirm = useConfirm();
  const query = useQuery(customFieldsQuery(project.id));
  const role = useQuery(roleQuery(project.id));
  const canManage =
    !project.archivedAt && ['Owner', 'Admin'].includes(role.data?.role ?? '');
  const [editor, setEditor] = useState<
    CustomFieldDefinition | null | undefined
  >();
  const [message, setMessage] = useState('');
  const [deleting, setDeleting] = useState<number>();
  async function remove(field: CustomFieldDefinition) {
    if (!(await confirm(t('deleteConfirm', { name: field.name })))) return;
    setDeleting(field.id);
    setMessage('');
    try {
      await api(`/projects/${project.id}/custom-fields/${field.id}`, {
        method: 'DELETE',
        headers: { 'If-Match': `"${field.version}"` },
      });
      await afterProjectWrite(
        client,
        project.id,
        customFieldsQuery(project.id).queryKey,
      );
      setMessage(t('deleted'));
    } catch (error) {
      setMessage(
        localizeMessage(
          error instanceof Error ? error.message : t('saveFailed'),
          language,
        ),
      );
    } finally {
      setDeleting(undefined);
    }
  }
  return (
    <section className="page custom-fields-page">
      <PageHeader
        guide="fields"
        eyebrow={t('eyebrow')}
        title={t('title')}
        description={t('description')}
        action={
          <button
            className="primary-action"
            disabled={!canManage || !query.data || query.data.length >= 20}
            onClick={() => {
              setMessage('');
              setEditor(null);
            }}
          >
            {t('add')}
          </button>
        }
      />
      <Link to="/tasks" className="secondary-action custom-fields-back">
        {t('tasks')}
      </Link>
      {!canManage && role.isSuccess && query.isSuccess && (
        <p className="custom-fields-help">{t('readonly')}</p>
      )}
      {message && (
        <p role="status" className="form-message">
          {message}
        </p>
      )}
      {query.isError || role.isError ? (
        <ErrorState
          detail={t('failed')}
          onRetry={() => {
            void query.refetch();
            void role.refetch();
          }}
        />
      ) : query.isPending || role.isPending ? (
        <Skeleton label={t('title')} />
      ) : (
        <>
          <p>{t('count', { count: query.data.length })}</p>
          {query.data.length === 0 ? (
            <EmptyState title={t('empty')} description={t('emptyHint')} />
          ) : (
            <ul className="custom-field-definitions">
              {query.data.map((field) => (
                <li key={field.id}>
                  <div>
                    <h2>{field.name}</h2>
                    <p>{f(field.kind)}</p>
                    {field.options.length > 0 && (
                      <ul>
                        {field.options.map((option) => (
                          <li key={option}>{option}</li>
                        ))}
                      </ul>
                    )}
                  </div>
                  {canManage && (
                    <div className="custom-field-actions">
                      <button
                        className="secondary-action"
                        disabled={deleting !== undefined}
                        onClick={() => {
                          setMessage('');
                          setEditor(field);
                        }}
                      >
                        {t('edit')}
                      </button>
                      <button
                        className="secondary-action"
                        disabled={deleting !== undefined}
                        onClick={() => {
                          void remove(field);
                        }}
                      >
                        {t('remove')}
                      </button>
                    </div>
                  )}
                </li>
              ))}
            </ul>
          )}
        </>
      )}
      {editor !== undefined && (
        <DefinitionEditor
          key={editor?.id ?? 'new'}
          projectId={project.id}
          field={editor}
          canManage={canManage}
          onClose={() => setEditor(undefined)}
          onSaved={async () => {
            await afterProjectWrite(
              client,
              project.id,
              customFieldsQuery(project.id).queryKey,
            );
            setEditor(undefined);
            setMessage(t('saved'));
          }}
        />
      )}
    </section>
  );
}

function DefinitionEditor({
  projectId,
  field,
  canManage,
  onClose,
  onSaved,
}: {
  projectId: number;
  field: CustomFieldDefinition | null;
  canManage: boolean;
  onClose: () => void;
  onSaved: () => Promise<void>;
}) {
  const t = useT(fieldsText),
    f = useT(customFieldText);
  const { language } = useLanguage();
  const confirm = useConfirm();
  const ui = useT(uiText);
  const [name, setName] = useState(field?.name ?? '');
  const [kind, setKind] = useState<CustomFieldKind>(field?.kind ?? 'TEXT');
  const [options, setOptions] = useState(field?.options.join('\n') ?? '');
  const [saving, setSaving] = useState(false),
    [error, setError] = useState('');
  const dirty =
    name !== (field?.name ?? '') ||
    kind !== (field?.kind ?? 'TEXT') ||
    options !== (field?.options.join('\n') ?? '');
  async function save(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError('');
    try {
      await api<CustomFieldDefinition>(
        `/projects/${projectId}/custom-fields${field ? `/${field.id}` : ''}`,
        {
          method: field ? 'PUT' : 'POST',
          ...(field ? { headers: { 'If-Match': `"${field.version}"` } } : {}),
          body: JSON.stringify({
            name,
            kind,
            options:
              kind === 'SELECT'
                ? options
                    .split(/\r?\n/)
                    .map((item) => item.trim())
                    .filter(Boolean)
                : [],
          }),
        },
      );
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
  return (
    <Drawer
      title={t(field ? 'edit' : 'add')}
      onClose={onClose}
      dirty={dirty}
      busy={saving}
    >
      <form className="editor-form" onSubmit={save}>
        <FormGuide topic="customFieldDefinition" />
        <fieldset disabled={saving || !canManage}>
          <label>
            {t('name')}
            <input
              required
              maxLength={80}
              value={name}
              onChange={(event) => setName(event.target.value)}
              placeholder={t('nameExample')}
            />
          </label>
          <label>
            {t('kind')}
            <select
              value={kind}
              disabled={!!field}
              onChange={(event) => {
                setKind(event.target.value as CustomFieldKind);
                setOptions('');
              }}
            >
              {kinds.map((item) => (
                <option value={item} key={item}>
                  {f(item)}
                </option>
              ))}
            </select>
          </label>
          {field && <p>{t('immutable')}</p>}
          {kind === 'SELECT' && (
            <label>
              {t('options')}
              <textarea
                required
                rows={6}
                maxLength={2500}
                value={options}
                onChange={(event) => setOptions(event.target.value)}
                placeholder={t('optionsExample')}
              />
            </label>
          )}
        </fieldset>
        {error && (
          <p className="form-message error" role="alert">
            {error}
          </p>
        )}
        {!canManage && <p>{t('readonly')}</p>}
        <div className="editor-actions">
          <button className="primary-action" disabled={saving || !canManage}>
            {t(saving ? 'saving' : 'save')}
          </button>
          <button
            type="button"
            className="secondary-action"
            disabled={saving}
            onClick={() => {
              if (!dirty) onClose();
              else
                void confirm(ui('discardChanges')).then((discard) => {
                  if (discard) onClose();
                });
            }}
          >
            {t('cancel')}
          </button>
        </div>
      </form>
    </Drawer>
  );
}
