import { useState, type FormEvent } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { api } from '../../api/client';
import type { Sprint } from '../../api/contracts';
import { useLanguage, useT } from '../../shared/i18n/LanguageProvider';
import { localizeMessage } from '../../shared/i18n/messages';
import { ErrorState, Skeleton } from '../../shared/ui/States';
import { Drawer, Notice } from '../../shared/ui/ui';
import {
  planOptionsQuery,
  type CapacityUnit,
  type PlanOptions,
  type Proposal,
  type StoryLock,
} from './sprintPlan';
import { sprintPlanText } from './sprintPlanText';
import './sprint-plan.css';

export function SprintPlanDrawer({
  projectId,
  sprint,
  onClose,
}: {
  projectId: number;
  sprint: Sprint;
  onClose: () => void;
}) {
  const t = useT(sprintPlanText);
  const [unit, setUnit] = useState<CapacityUnit>('POINTS');
  return (
    <Drawer title={`${t('title')} · ${sprint.name}`} onClose={onClose}>
      <div className="sprint-plan">
        <p>{t('help')}</p>
        <label>
          {t('unit')}
          <select
            value={unit}
            aria-label={t('unit')}
            onChange={(event) => setUnit(event.target.value as CapacityUnit)}
          >
            <option value="POINTS">{t('POINTS')}</option>
            <option value="HOURS">{t('HOURS')}</option>
          </select>
        </label>
        <PlanLoader
          key={unit}
          projectId={projectId}
          sprintId={sprint.id}
          unit={unit}
        />
      </div>
    </Drawer>
  );
}

function PlanLoader({
  projectId,
  sprintId,
  unit,
}: {
  projectId: number;
  sprintId: number;
  unit: CapacityUnit;
}) {
  const t = useT(sprintPlanText);
  const query = useQuery(planOptionsQuery(projectId, sprintId, unit));
  if (query.isPending) return <Skeleton label={t('loading')} />;
  if (query.isError)
    return (
      <ErrorState
        detail={t('loadFailed')}
        onRetry={() => void query.refetch()}
      />
    );
  return <Scenario projectId={projectId} options={query.data} />;
}

function Scenario({
  projectId,
  options,
}: {
  projectId: number;
  options: PlanOptions;
}) {
  const t = useT(sprintPlanText);
  const { language } = useLanguage();
  const [team, setTeam] = useState('');
  const [capacity, setCapacity] = useState<Record<string, string>>({});
  const [locks, setLocks] = useState<Record<string, StoryLock | ''>>({});
  const mutation = useMutation({
    mutationFn: () =>
      api<Proposal>(`/projects/${projectId}/sprint-plan/optimize`, {
        method: 'POST',
        body: JSON.stringify({
          sprintId: options.sprintId,
          unit: options.unit,
          totalCapacity: team === '' ? null : Number(team),
          memberCapacities: Object.fromEntries(
            Object.entries(capacity)
              .filter(([, value]) => value !== '')
              .map(([id, value]) => [id, Number(value)]),
          ),
          lockedIn: Object.keys(locks)
            .filter((id) => locks[id] === 'in')
            .map(Number),
          lockedOut: Object.keys(locks)
            .filter((id) => locks[id] === 'out')
            .map(Number),
        }),
      }),
  });
  function optimize(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    mutation.mutate();
  }
  function reset() {
    setTeam('');
    setCapacity({});
    setLocks({});
    mutation.reset();
  }
  return (
    <>
      <dl>
        <div>
          <dt>{t('configured')}</dt>
          <dd>{options.configuredCapacity}</dd>
        </div>
        <div>
          <dt>{t('reserved')}</dt>
          <dd>{options.reservedEffort}</dd>
        </div>
      </dl>
      {options.unestimatedCommitments > 0 && (
        <p role="status">{t('missing')}</p>
      )}
      <form onSubmit={optimize}>
        <fieldset disabled={mutation.isPending}>
          <legend>{t('overrides')}</legend>
          <p>{t('memberHint')}</p>
          <label>
            {t('team')}
            <input
              type="number"
              min={0}
              max={1_000_000}
              step={1}
              placeholder={String(options.configuredCapacity)}
              value={team}
              onChange={(event) => {
                setTeam(event.target.value);
                mutation.reset();
              }}
            />
          </label>
          {options.members.map((member) => (
            <label key={member.userId}>
              {member.email}
              <input
                type="number"
                min={0}
                max={1_000_000}
                step={1}
                placeholder={String(member.configuredCapacity)}
                value={capacity[member.userId] ?? ''}
                onChange={(event) => {
                  setCapacity((old) => ({
                    ...old,
                    [member.userId]: event.target.value,
                  }));
                  mutation.reset();
                }}
              />
            </label>
          ))}
        </fieldset>
        <fieldset disabled={mutation.isPending}>
          <legend>{t('story')}</legend>
          {options.stories.length === 0 && <p>{t('empty')}</p>}
          {options.stories.map((story) => (
            <div key={story.id} className="sprint-plan-story">
              <strong>{story.title}</strong>
              <span>
                {t('value')} {story.value} · {story.effort} {t(options.unit)}
              </span>
              {story.eligibility === 'READY' ? (
                <select
                  aria-label={`${t('story')} · ${story.title} #${story.id}`}
                  value={locks[story.id] ?? ''}
                  onChange={(event) => {
                    setLocks((old) => ({
                      ...old,
                      [story.id]: event.target.value as StoryLock | '',
                    }));
                    mutation.reset();
                  }}
                >
                  <option value="">{t('none')}</option>
                  <option value="in">{t('in')}</option>
                  <option value="out">{t('out')}</option>
                </select>
              ) : (
                <span>{t(story.eligibility)}</span>
              )}
            </div>
          ))}
        </fieldset>
        <div className="form-actions">
          <button
            type="submit"
            className="primary-action"
            disabled={mutation.isPending || options.unestimatedCommitments > 0}
          >
            {t(mutation.isPending ? 'running' : 'run')}
          </button>
          <button
            type="button"
            className="secondary-action"
            onClick={reset}
            disabled={mutation.isPending}
          >
            {t('clear')}
          </button>
        </div>
        <Notice
          error={localizeMessage(mutation.error?.message ?? '', language)}
        />
      </form>
      {mutation.data && <Result proposal={mutation.data} />}
    </>
  );
}

function Result({ proposal }: { proposal: Proposal }) {
  const t = useT(sprintPlanText);
  const { plan, options } = proposal;
  const name = (id: string) =>
    options.stories.find((story) => String(story.id) === id)?.title ?? `#${id}`;
  const resourceName = (id: string) =>
    id === 'total'
      ? t('teamLabel')
      : (options.members.find((member) => `member:${member.userId}` === id)
          ?.email ?? id);
  return (
    <section className="sprint-plan-result">
      <h3 role="status">{t(plan.status)}</h3>
      {plan.status !== 'INFEASIBLE' && (
        <>
          <dl>
            <div>
              <dt>{t('value')}</dt>
              <dd>{plan.objective}</dd>
            </div>
            {plan.bestBound != null && (
              <div>
                <dt>{t('bound')}</dt>
                <dd>{plan.bestBound}</dd>
              </div>
            )}
            {plan.relativeGap != null && (
              <div>
                <dt>{t('gap')}</dt>
                <dd>{(100 * plan.relativeGap).toFixed(1)}%</dd>
              </div>
            )}
          </dl>
          <h4>{t('selected')}</h4>
          {plan.selectedStoryIds.length ? (
            <ul>
              {plan.selectedStoryIds.map((id) => (
                <li key={id}>{name(id)}</li>
              ))}
            </ul>
          ) : (
            <p>{t('noneSelected')}</p>
          )}
          <h4>{t('resource')}</h4>
          <ul>
            {plan.explanation.resources.map((row) => (
              <li key={row.resource}>
                {resourceName(row.resource)} · {row.used} / {row.available}{' '}
                {row.binding && `· ${t('binding')}`}
              </li>
            ))}
          </ul>
        </>
      )}
      {plan.explanation.conflicts.length > 0 && (
        <ul>
          {plan.explanation.conflicts.map((conflict, index) => (
            <li key={index}>
              {t(conflict.kind)} · {conflict.storyIds.map(name).join(', ')}
              {conflict.resource && ` · ${resourceName(conflict.resource)}`}
            </li>
          ))}
        </ul>
      )}
      {plan.explanation.excluded.length > 0 && (
        <details>
          <summary>{t('excluded')}</summary>
          <ul>
            {plan.explanation.excluded.map((row) => (
              <li key={row.storyId}>
                {name(row.storyId)} · {t(row.reason)}
                {row.relatedStoryIds.length > 0 &&
                  ` · ${row.relatedStoryIds.map(name).join(', ')}`}
              </li>
            ))}
          </ul>
        </details>
      )}
    </section>
  );
}
