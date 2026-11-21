import { useQuery, type UseQueryResult } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router';
import type { Project } from '../../api/contracts';
import { roleQuery } from '../../api/queries';
import { useLanguage, useT } from '../../shared/i18n/LanguageProvider';
import { localizeMessage } from '../../shared/i18n/messages';
import { EmptyState, ErrorState, Skeleton } from '../../shared/ui/States';
import { Notice, useConfirm } from '../../shared/ui/ui';
import { automationText } from './i18n';
import {
  githubDeliveriesQuery,
  githubQuery,
  rulesQuery,
  runsQuery,
  type IssuedGithub,
  type GithubDelivery,
  type GithubRules,
  type Rule,
  useAutomationActions,
} from './queries';
import { RuleEditor } from './RuleEditor';
import './automation.css';

export default function AutomationView({
  project,
}: {
  project: Project | undefined;
}) {
  const t = useT(automationText);
  return (
    <section className="page automation-page">
      <header className="page-heading">
        <h1>{t('title')}</h1>
      </header>
      {project ? (
        <ProjectAutomation key={project.id} project={project} />
      ) : (
        <EmptyState title={t('projectNeeded')} />
      )}
    </section>
  );
}

function ProjectAutomation({ project }: { project: Project }) {
  const t = useT(automationText);
  const { language, format } = useLanguage();
  const confirm = useConfirm();
  const role = useQuery(roleQuery(project.id));
  const allowed = ['Owner', 'Admin'].includes(role.data?.role ?? '');
  const rules = useQuery({ ...rulesQuery(project.id), enabled: allowed });
  const runs = useQuery({ ...runsQuery(project.id), enabled: allowed });
  const actions = useAutomationActions(project.id);
  const [editing, setEditing] = useState<Rule | 'new'>();
  if (role.isPending) return <Skeleton />;
  if (role.isError)
    return (
      <ErrorState
        detail={t('loadFailed')}
        onRetry={() => void role.refetch()}
      />
    );
  if (!allowed) return <p>{t('managedOnly')}</p>;
  if (rules.isError || runs.isError)
    return (
      <ErrorState
        detail={t('loadFailed')}
        onRetry={() => {
          void rules.refetch();
          void runs.refetch();
        }}
      />
    );
  if (rules.isPending || runs.isPending) return <Skeleton />;

  function edit(rule: Rule | 'new') {
    actions.save.reset();
    setEditing(rule);
  }

  return (
    <>
      {project.archivedAt ? (
        <p>{t('archived')}</p>
      ) : (
        <button
          type="button"
          className="primary-action"
          onClick={() => edit('new')}
        >
          {t('newRule')}
        </button>
      )}
      <Notice
        error={
          actions.remove.error
            ? localizeMessage(actions.remove.error.message, language)
            : ''
        }
      />
      {rules.data.length === 0 ? (
        <p>{t('emptyRules')}</p>
      ) : (
        <div className="automation-rules">
          {rules.data.map((rule) => (
            <article key={rule.id} className="flow-panel automation-rule">
              <div>
                <h2>{rule.name}</h2>
                <p>
                  {t(rule.trigger)} · {t(rule.enabled ? 'active' : 'inactive')}
                </p>
                <pre>{rule.source}</pre>
              </div>
              {!project.archivedAt && (
                <div className="card-actions">
                  <button type="button" onClick={() => edit(rule)}>
                    {t('edit')}
                  </button>
                  <button
                    type="button"
                    className="danger-action"
                    disabled={actions.remove.isPending}
                    onClick={async () => {
                      if (await confirm(t('removeRule')))
                        actions.remove.mutate(rule);
                    }}
                  >
                    {t('delete')}
                  </button>
                </div>
              )}
            </article>
          ))}
        </div>
      )}
      {editing && (
        <RuleEditor
          projectId={project.id}
          rule={editing === 'new' ? undefined : editing}
          saving={actions.save.isPending}
          error={actions.save.error}
          onClose={() => setEditing(undefined)}
          onSave={(draft) =>
            actions.save.mutate(
              { draft, rule: editing === 'new' ? undefined : editing },
              {
                onSuccess: () => setEditing(undefined),
              },
            )
          }
        />
      )}
      <GithubPanel project={project} actions={actions} />
      <section className="flow-panel chart-table">
        <div className="collection-actions">
          <h2>{t('log')}</h2>
          <button
            type="button"
            className="secondary-action"
            disabled={runs.isFetching}
            onClick={() => void runs.refetch()}
          >
            {t('refresh')}
          </button>
        </div>
        {runs.data.length === 0 ? (
          <p>{t('noRuns')}</p>
        ) : (
          <div className="chart-table-scroll">
            <table aria-label={t('log')}>
              <thead>
                <tr>
                  <th scope="col">{t('happened')}</th>
                  <th scope="col">{t('rule')}</th>
                  <th scope="col">{t('task')}</th>
                  <th scope="col">{t('outcome')}</th>
                </tr>
              </thead>
              <tbody>
                {runs.data.map((run) => (
                  <tr key={run.id}>
                    <td>
                      <time dateTime={run.executedAt}>
                        {format.dateTime(run.executedAt)}
                      </time>
                    </td>
                    <td>{run.ruleName}</td>
                    <td>
                      {run.taskKey ? (
                        <Link
                          to={`/tasks?task=${encodeURIComponent(run.taskKey)}`}
                        >
                          {run.taskKey}
                        </Link>
                      ) : (
                        `#${run.taskId}`
                      )}
                    </td>
                    <td>
                      {t(run.outcome)} · {localizeMessage(run.detail, language)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </>
  );
}

function GithubPanel({
  project,
  actions,
}: {
  project: Project;
  actions: ReturnType<typeof useAutomationActions>;
}) {
  const t = useT(automationText);
  const { language } = useLanguage();
  const confirm = useConfirm();
  const settings = useQuery(githubQuery(project.id));
  const [expanded, setExpanded] = useState(false);
  const deliveries = useQuery({
    ...githubDeliveriesQuery(project.id),
    enabled: expanded,
  });
  const [repository, setRepository] = useState<string>();
  const [issued, setIssued] = useState<IssuedGithub>();
  const [draftRules, setDraftRules] = useState<GithubRules>();
  const current = settings.data ?? issued?.settings;
  const error =
    actions.github.error ??
    actions.disconnect.error ??
    actions.githubRules.error;
  return (
    <details
      className="flow-panel chart-table"
      onToggle={(event) => setExpanded(event.currentTarget.open)}
    >
      <summary>{t('github')}</summary>
      {settings.isPending && <Skeleton />}
      {settings.isError && (
        <ErrorState
          detail={t('loadFailed')}
          onRetry={() => void settings.refetch()}
        />
      )}
      <Notice error={error ? localizeMessage(error.message, language) : ''} />
      {current && (
        <>
          <label>
            {t('url')}{' '}
            <input
              readOnly
              value={`${window.location.origin}/api/automation/github/${issued?.settings.endpointId ?? current.endpointId}`}
            />
          </label>
          <p>{t('hookHelp')}</p>
        </>
      )}
      {issued && (
        <>
          <label>
            {t('secret')}{' '}
            <input readOnly value={issued.secret} autoComplete="off" />
          </label>
          <p>{t('secretHelp')}</p>
          <button
            type="button"
            onClick={() => {
              setIssued(undefined);
              actions.github.reset();
            }}
          >
            {t('hideSecret')}
          </button>
        </>
      )}
      {!project.archivedAt && settings.isSuccess && (
        <form
          className="editor-form"
          onSubmit={async (event) => {
            event.preventDefault();
            const selected = repository ?? current?.repository ?? '';
            if (current && !(await confirm(t('rotateConfirm')))) return;
            actions.github.mutate(selected, {
              onSuccess: (value) => {
                setIssued(value);
                setRepository(undefined);
              },
            });
          }}
        >
          <label>
            {t('repository')}{' '}
            <input
              required
              maxLength={180}
              pattern={String.raw`[A-Za-z0-9\-]+\/[A-Za-z0-9._\-]+`}
              placeholder="owner/repository"
              value={repository ?? current?.repository ?? ''}
              onChange={(event) => setRepository(event.target.value)}
            />
          </label>
          <div className="card-actions">
            <button
              className="primary-action"
              disabled={
                actions.github.isPending || actions.disconnect.isPending
              }
            >
              {t(current ? 'rotate' : 'configure')}
            </button>
            {current && (
              <button
                type="button"
                className="danger-action"
                disabled={
                  actions.github.isPending || actions.disconnect.isPending
                }
                onClick={async () => {
                  if (await confirm(t('disconnectConfirm')))
                    actions.disconnect.mutate(undefined, {
                      onSuccess: () => {
                        setIssued(undefined);
                        setRepository(undefined);
                        setDraftRules(undefined);
                        actions.github.reset();
                      },
                    });
                }}
              >
                {t('disconnect')}
              </button>
            )}
          </div>
        </form>
      )}
      {current && (
        <form
          className="editor-form"
          onSubmit={(event) => {
            event.preventDefault();
            if (draftRules)
              actions.githubRules.mutate(draftRules, {
                onSuccess: () => setDraftRules(undefined),
              });
          }}
        >
          <fieldset
            disabled={
              !!project.archivedAt ||
              actions.githubRules.isPending ||
              actions.disconnect.isPending
            }
          >
            <legend>{t('githubStatusRules')}</legend>
            <label>
              <input
                type="checkbox"
                checked={
                  draftRules?.reviewOnOpen ?? current.reviewOnOpen ?? false
                }
                onChange={(event) =>
                  setDraftRules({
                    doneOnMerge: draftRules?.doneOnMerge ?? current.doneOnMerge,
                    reviewOnOpen: event.target.checked,
                  })
                }
              />{' '}
              {t('reviewOnOpen')}
            </label>
            <label>
              <input
                type="checkbox"
                checked={
                  draftRules?.doneOnMerge ?? current.doneOnMerge ?? false
                }
                onChange={(event) =>
                  setDraftRules({
                    reviewOnOpen:
                      draftRules?.reviewOnOpen ?? current.reviewOnOpen,
                    doneOnMerge: event.target.checked,
                  })
                }
              />{' '}
              {t('doneOnMerge')}
            </label>
            <button type="submit" disabled={!draftRules}>
              {t('saveGithubStatusRules')}
            </button>
          </fieldset>
        </form>
      )}
      {expanded && <GithubDeliveryHistory deliveries={deliveries} />}
    </details>
  );
}

function GithubDeliveryHistory({
  deliveries,
}: {
  deliveries: UseQueryResult<GithubDelivery[]>;
}) {
  const t = useT(automationText);
  const { format } = useLanguage();
  if (deliveries.isPending) return <Skeleton />;
  if (deliveries.isError)
    return (
      <ErrorState
        detail={t('loadFailed')}
        onRetry={() => void deliveries.refetch()}
      />
    );
  return (
    <section aria-label={t('githubDeliveries')}>
      <h3>{t('githubDeliveries')}</h3>
      {deliveries.data?.length ? (
        <ul>
          {deliveries.data.map((entry) => (
            <li key={entry.id}>
              <strong>{entry.repository}</strong> ·{' '}
              {t(entry.event === 'push' ? 'githubPush' : 'githubPullRequest')}
              {entry.action && (
                <> · {t('githubAction', { action: entry.action })}</>
              )}
              {' · '}
              {t('githubDeliveryCounts', {
                linked: entry.linkedTasks,
                moved: entry.movedTasks,
              })}
              {' · '}
              <time dateTime={entry.receivedAt}>
                {format.dateTime(entry.receivedAt)}
              </time>
            </li>
          ))}
        </ul>
      ) : (
        <p>{t('noGithubDeliveries')}</p>
      )}
    </section>
  );
}
