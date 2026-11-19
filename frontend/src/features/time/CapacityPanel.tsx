import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useT } from '../../shared/i18n/LanguageProvider';
import { ErrorState, Skeleton } from '../../shared/ui/States';
import type { MemberCapacity } from './contracts';
import { timeText } from './i18n';
import { capacityQuery } from './queries';
import { CapacityEditor } from './TimeEditors';

export function CapacityPanel({
  projectId,
  sprintId,
  userId,
  canWrite,
  canManage,
}: {
  projectId: number;
  sprintId: number;
  userId: number;
  canWrite: boolean;
  canManage: boolean;
}) {
  const t = useT(timeText);
  const query = useQuery(capacityQuery(projectId, sprintId));
  const [editing, setEditing] = useState<MemberCapacity>();
  if (query.isError)
    return (
      <ErrorState detail={t('failed')} onRetry={() => void query.refetch()} />
    );
  if (query.isPending) return <Skeleton label={t('loading')} />;
  const data = query.data;
  return (
    <section className="time-panel">
      <h2>
        {t('capacity')} · {data.sprintName}
      </h2>
      <p className="time-hint">{t('capacityHelp')}</p>
      <p>
        {t('days')}: {data.workingDays}
      </p>
      <dl className="time-stats">
        <div>
          <dt>{t('available')}</dt>
          <dd>{t('number', { value: data.team.availableMinutes })}</dd>
        </div>
        <div>
          <dt>{t('committed')}</dt>
          <dd>{t('number', { value: data.team.committedMinutes })}</dd>
        </div>
        <div>
          <dt>{t('load')}</dt>
          <dd>{data.team.loadPercent ?? '—'}</dd>
        </div>
      </dl>
      {data.warnings.length > 0 && (
        <ul className="time-warnings">
          {data.warnings.map((warning, index) => (
            <li key={`${warning.code}-${index}`}>
              {t(warning.code)}{' '}
              {warning.userId !== null
                ? data.members.find(
                    (member) => member.userId === warning.userId,
                  )?.email
                : ''}
            </li>
          ))}
        </ul>
      )}
      <div className="time-members">
        {data.members.map((member) => (
          <article
            key={member.userId}
            className={
              member.overbooked || member.pointsOverbooked
                ? 'time-overbooked'
                : ''
            }
          >
            <h3>{member.email}</h3>
            {!member.configured && <p>{t('notSet')}</p>}
            {(member.overbooked || member.pointsOverbooked) && (
              <p className="status-badge">{t('overbooked')}</p>
            )}
            <dl className="time-stats">
              <div>
                <dt>{t('available')}</dt>
                <dd>{t('number', { value: member.availableMinutes })}</dd>
              </div>
              <div>
                <dt>{t('committed')}</dt>
                <dd>{t('number', { value: member.committedMinutes })}</dd>
              </div>
              <div>
                <dt>{t('load')}</dt>
                <dd>{member.loadPercent ?? '—'}</dd>
              </div>
              <div>
                <dt>{t('logged')}</dt>
                <dd>{t('number', { value: member.loggedMinutes })}</dd>
              </div>
              {member.effectivePoints !== null && (
                <>
                  <div>
                    <dt>{t('points')}</dt>
                    <dd>{member.effectivePoints}</dd>
                  </div>
                  <div>
                    <dt>{t('committedPoints')}</dt>
                    <dd>{member.committedPoints}</dd>
                  </div>
                </>
              )}
            </dl>
            {canWrite && (canManage || member.userId === userId) && (
              <button
                className="secondary-action"
                onClick={() => setEditing(member)}
              >
                {t('configure')}
              </button>
            )}
          </article>
        ))}
      </div>
      {canWrite && editing && (
        <CapacityEditor
          projectId={projectId}
          sprintId={sprintId}
          member={editing}
          onClose={() => setEditing(undefined)}
        />
      )}
    </section>
  );
}
