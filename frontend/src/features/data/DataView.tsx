import { useRef, useState, type ChangeEvent } from 'react';
import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query';
import { api } from '../../api/client';
import type { Project } from '../../api/contracts';
import { customFieldsQuery } from '../../api/customFields';
import { afterProjectWrite, roleQuery } from '../../api/queries';
import { useLanguage, useT } from '../../shared/i18n/LanguageProvider';
import { localizeMessage } from '../../shared/i18n/messages';
import { EmptyState } from '../../shared/ui/States';
import { Notice, PageHeader, useConfirm } from '../../shared/ui/ui';
import type { ImportPreview, ImportRequest, ImportResult } from './contracts';
import { dataText } from './i18n';
import { auditQuery, batchesQuery, transferKey } from './queries';
import './data.css';
const states = ['To Do', 'In Progress', 'Review', 'Done'];
const builtin = [
  'title',
  'description',
  'status',
  'storyPoints',
  'assigneeEmail',
  'labels',
  'dueAt',
  'sourceKey',
  'createdAt',
  'startedAt',
  'completedAt',
] as const;
export function DataView({ project }: { project: Project | undefined }) {
  const t = useT(dataText);
  return (
    <section className="page data-page">
      <PageHeader
        guide="data"
        eyebrow="DATA"
        title={t('title')}
        description={t('description')}
      />
      {project ? (
        <ProjectData key={project.id} project={project} />
      ) : (
        <EmptyState title={t('choose')} />
      )}
    </section>
  );
}
function ProjectData({ project }: { project: Project }) {
  const t = useT(dataText);
  const { language } = useLanguage();
  const client = useQueryClient();
  const confirm = useConfirm();
  const role = useQuery(roleQuery(project.id));
  const fields = useQuery(customFieldsQuery(project.id));
  const canManage = ['Owner', 'Admin'].includes(role.data?.role ?? '');
  const canWrite =
    !project.archivedAt &&
    ['Owner', 'Admin', 'Member'].includes(role.data?.role ?? '');
  const batches = useQuery(batchesQuery(project.id));
  const audit = useInfiniteQuery({
    ...auditQuery(project.id),
    enabled: canManage,
  });
  const [draft, setDraft] = useState<ImportRequest>({
    format: 'CSV',
    content: '',
    mapping: {},
    statuses: {},
    truncateText: false,
    preserveHistory: false,
  });
  const [preview, setPreview] = useState<{
    request: ImportRequest;
    value: ImportPreview;
  }>();
  const [result, setResult] = useState<ImportResult>();
  const [fileError, setFileError] = useState('');
  const [reading, setReading] = useState(false);
  const fileGeneration = useRef(0);
  const previewMutation = useMutation({
    mutationFn: (request: ImportRequest) =>
      api<ImportPreview>(`/projects/${project.id}/imports/preview`, {
        method: 'POST',
        body: JSON.stringify(request),
      }),
  });
  const commit = useMutation({
    mutationFn: (request: ImportRequest) =>
      api<ImportResult>(`/projects/${project.id}/imports`, {
        method: 'POST',
        body: JSON.stringify(request),
      }),
    onSuccess: async (value) => {
      setResult(value);
      await afterProjectWrite(client, project.id, transferKey(project.id));
    },
  });
  const busy = reading || previewMutation.isPending || commit.isPending;
  const current = preview?.request === draft;
  function edit(change: Partial<ImportRequest>) {
    setDraft((value) => ({ ...value, ...change }));
    setResult(undefined);
    previewMutation.reset();
    commit.reset();
  }
  async function loadFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    const generation = ++fileGeneration.current;
    edit({ content: '', mapping: {}, statuses: {} });
    setPreview(undefined);
    setFileError('');
    if (!file) return;
    if (file.size > 1_000_000) {
      setFileError(t('oversize'));
      return;
    }
    setReading(true);
    try {
      const content = await file.text();
      if (generation !== fileGeneration.current) return;
      edit({
        content,
        format: file.name.toLowerCase().endsWith('.csv') ? 'CSV' : 'JIRA_JSON',
        mapping: {},
        statuses: {},
      });
    } catch {
      if (generation === fileGeneration.current) setFileError(t('failure'));
    } finally {
      if (generation === fileGeneration.current) setReading(false);
    }
  }
  async function inspect() {
    const request = draft;
    setFileError('');
    setResult(undefined);
    try {
      const value = await previewMutation.mutateAsync(request);
      setPreview({ request, value });
    } catch {
      /* Mutation exposes the error and preserves the draft. */
    }
  }
  async function save() {
    if (
      !preview ||
      !current ||
      !preview.value.canCommit ||
      !(await confirm(t('confirm')))
    )
      return;
    try {
      await commit.mutateAsync({
        ...draft,
        expectedFingerprint: preview.value.fingerprint,
      });
    } catch {
      /* Keep preview and draft available for correction/retry. */
    }
  }
  const error =
    fileError ||
    previewMutation.error?.message ||
    commit.error?.message ||
    batches.error?.message ||
    fields.error?.message ||
    role.error?.message ||
    '';
  const targets: [string, string][] = builtin
    .map<[string, string]>((key) => [
      key,
      t(
        key === 'title'
          ? 'titleField'
          : key === 'description'
            ? 'descriptionField'
            : key,
      ),
    ])
    .concat(
      (fields.data ?? []).map<[string, string]>((field) => [
        `custom:${field.id}`,
        field.name,
      ]),
    );
  const statusValues = [
    ...new Set(preview?.value.rows.map((row) => row.sourceStatus) ?? []),
  ].filter((value) => value.length > 0);
  return (
    <>
      <section className="profile-card">
        <h2>{t('importHeading')}</h2>
        <p>{t('limits')}</p>
        {!canWrite && <p>{t('readonly')}</p>}
        <fieldset disabled={busy || !canWrite}>
          <label>
            {t('file')}
            <input
              type="file"
              accept=".csv,.json,text/csv,application/json"
              onChange={loadFile}
            />
          </label>
          <label>
            {t('format')}
            <select
              value={draft.format}
              onChange={(e) =>
                edit({
                  format: e.target.value as ImportRequest['format'],
                  mapping: {},
                  statuses: {},
                })
              }
            >
              <option value="CSV">CSV</option>
              <option value="JIRA_JSON">Jira JSON</option>
            </select>
          </label>
          <label className="checkbox-row">
            <input
              type="checkbox"
              checked={draft.truncateText}
              onChange={(e) => edit({ truncateText: e.target.checked })}
            />
            {t('truncate')}
          </label>
          {canManage && (
            <label className="checkbox-row">
              <input
                type="checkbox"
                checked={draft.preserveHistory}
                onChange={(e) => edit({ preserveHistory: e.target.checked })}
              />
              {t('history')}
            </label>
          )}
          <p>{t('historyHelp')}</p>
          {preview && (
            <details open>
              <summary>{t('mapping')}</summary>
              <div className="data-mapping">
                {targets.map(([target, label]) => (
                  <label key={target}>
                    {label}
                    <select
                      value={
                        draft.mapping[target] ??
                        (Object.keys(draft.mapping).length === 0
                          ? (preview.value.mapping[target] ?? '')
                          : '')
                      }
                      onChange={(e) => {
                        const mapping = Object.fromEntries(
                          Object.entries(
                            Object.keys(draft.mapping).length
                              ? draft.mapping
                              : preview.value.mapping,
                          ).filter(([key]) => key !== target),
                        );
                        if (e.target.value) mapping[target] = e.target.value;
                        edit({ mapping });
                      }}
                    >
                      <option value="">{t('omit')}</option>
                      {preview.value.columns.map((col) => (
                        <option key={col}>{col}</option>
                      ))}
                    </select>
                  </label>
                ))}
              </div>
            </details>
          )}
          {statusValues.length > 0 && (
            <details open>
              <summary>{t('statuses')}</summary>
              <div className="data-mapping">
                {statusValues.map((value) => (
                  <label key={value}>
                    {value}
                    <select
                      aria-label={value}
                      value={draft.statuses[value] ?? ''}
                      onChange={(e) => {
                        const statuses = Object.fromEntries(
                          Object.entries(draft.statuses).filter(
                            ([key]) => key !== value,
                          ),
                        );
                        if (e.target.value) statuses[value] = e.target.value;
                        edit({ statuses });
                      }}
                    >
                      <option value="">—</option>
                      {states.map((state) => (
                        <option key={state}>{state}</option>
                      ))}
                    </select>
                  </label>
                ))}
              </div>
            </details>
          )}
          <button
            className="secondary-action"
            type="button"
            disabled={!draft.content}
            onClick={inspect}
          >
            {busy ? t('working') : t('preview')}
          </button>
        </fieldset>
        <Notice error={localizeMessage(error, language)} />
        {preview && !current && <p role="status">{t('refreshPreview')}</p>}
        {preview && (
          <>
            <h3>{t('review')}</h3>
            <p>
              {t('created')}: {preview.value.createCount} · {t('skipped')}:{' '}
              {preview.value.skipCount}
            </p>
            <div
              className="data-scroll"
              role="region"
              aria-label={t('review')}
              // eslint-disable-next-line jsx-a11y/no-noninteractive-tabindex -- Keyboard users must scroll the bounded table region.
              tabIndex={0}
            >
              <table>
                <thead>
                  <tr>
                    <th>{t('row')}</th>
                    <th>{t('titleField')}</th>
                    <th>{t('status')}</th>
                    <th>{t('messages')}</th>
                  </tr>
                </thead>
                <tbody>
                  {preview.value.rows.map((row) => (
                    <tr key={row.rowNumber}>
                      <td>{row.rowNumber}</td>
                      <td>
                        {row.title}
                        <small>{row.sourceKey}</small>
                        <details>
                          <summary>{t('details')}</summary>
                          <dl>
                            {(
                              [
                                ['description', row.description],
                                ['storyPoints', String(row.storyPoints)],
                                ['assigneeEmail', row.assigneeEmail],
                                ['labels', row.labels.join('; ')],
                                ['dueAt', row.dueAt],
                                ['createdAt', row.createdAt],
                                ['startedAt', row.startedAt],
                                ['completedAt', row.completedAt],
                              ] as const
                            ).map(([key, value]) => (
                              <div key={key}>
                                <dt>
                                  {t(
                                    key === 'description'
                                      ? 'descriptionField'
                                      : key,
                                  )}
                                </dt>
                                <dd>{value || '—'}</dd>
                              </div>
                            ))}
                            {Object.entries(row.customValues).map(
                              ([id, value]) => (
                                <div key={id}>
                                  <dt>
                                    {fields.data?.find(
                                      (field) => String(field.id) === id,
                                    )?.name ?? id}
                                  </dt>
                                  <dd>{value}</dd>
                                </div>
                              ),
                            )}
                          </dl>
                        </details>
                      </td>
                      <td>{row.status}</td>
                      <td>
                        {row.duplicate
                          ? t('duplicate')
                          : row.errors.length === 0
                            ? t('valid')
                            : null}
                        {[...row.errors, ...row.warnings].map(
                          (message, index) => (
                            <p key={index}>
                              {localizeMessage(message, language)}
                            </p>
                          ),
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <button
              className="primary-action"
              type="button"
              disabled={
                busy ||
                !canWrite ||
                !current ||
                !preview.value.canCommit ||
                !!result
              }
              onClick={save}
            >
              {commit.isPending ? t('working') : t('commit')}
            </button>
          </>
        )}
        {result && (
          <p role="status">
            {t('result')}. {t('created')}: {result.createdCount} ·{' '}
            {t('skipped')}: {result.skippedCount}
          </p>
        )}
      </section>
      <section className="profile-card">
        <h2>{t('batches')}</h2>
        {batches.isPending ? (
          <p>{t('working')}</p>
        ) : !batches.data?.length ? (
          <p>{t('emptyBatches')}</p>
        ) : (
          <ul>
            {batches.data.map((batch) => (
              <li key={batch.id}>
                {new Date(batch.createdAt).toLocaleString(
                  language === 'SV' ? 'sv-SE' : 'en-GB',
                )}{' '}
                · {batch.format} · {t('created')}: {batch.createdCount} ·{' '}
                {t('skipped')}: {batch.skippedCount}
              </li>
            ))}
          </ul>
        )}
      </section>
      {canManage && (
        <section className="profile-card">
          <h2>{t('audit')}</h2>
          <p>{t('auditHelp')}</p>
          <Notice
            error={localizeMessage(audit.error?.message ?? '', language)}
          />
          {audit.isPending ? (
            <p>{t('working')}</p>
          ) : !audit.data?.pages[0]?.items.length ? (
            <p>{t('emptyAudit')}</p>
          ) : (
            <div
              className="data-scroll"
              role="region"
              aria-label={t('audit')}
              // eslint-disable-next-line jsx-a11y/no-noninteractive-tabindex -- Keyboard users must scroll the bounded table region.
              tabIndex={0}
            >
              <table>
                <thead>
                  <tr>
                    <th>{t('date')}</th>
                    <th>{t('actor')}</th>
                    <th>{t('action')}</th>
                    <th>{t('target')}</th>
                  </tr>
                </thead>
                <tbody>
                  {audit.data.pages
                    .flatMap((page) => page.items)
                    .map((entry) => (
                      <tr key={entry.id}>
                        <td>
                          {new Date(entry.createdAt).toLocaleString(
                            language === 'SV' ? 'sv-SE' : 'en-GB',
                          )}
                        </td>
                        <td>
                          #{entry.actorUserId} · {entry.actorRole}
                        </td>
                        <td>{entry.action}</td>
                        <td>
                          {entry.targetType} {entry.targetId}
                        </td>
                      </tr>
                    ))}
                </tbody>
              </table>
            </div>
          )}
          {audit.hasNextPage && (
            <button
              type="button"
              className="secondary-action"
              disabled={audit.isFetchingNextPage}
              onClick={() => void audit.fetchNextPage()}
            >
              {t('more')}
            </button>
          )}
        </section>
      )}
    </>
  );
}
