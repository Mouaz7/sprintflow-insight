import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router';
import type { Project } from '../../api/contracts';
import { roleQuery } from '../../api/queries';
import { useT } from '../../shared/i18n/LanguageProvider';
import { EmptyState, ErrorState, Skeleton } from '../../shared/ui/States';
import { PageHeader } from '../../shared/ui/ui';
import { pokerText } from './i18n';
import { pokerListQuery } from './queries';
import { PokerRoom } from './PokerRoom';
import { StartSession } from './StartSession';

export function PokerView({ project }: { project: Project | undefined }) {
  const t = useT(pokerText);
  return (
    <section className="page poker-page">
      <PageHeader
        guide="poker"
        eyebrow={t('eyebrow')}
        title={t('title')}
        description={t('description')}
      />
      {project ? (
        <ProjectPoker key={project.id} project={project} />
      ) : (
        <EmptyState title={t('noProject')} />
      )}
    </section>
  );
}
function ProjectPoker({ project }: { project: Project }) {
  const t = useT(pokerText);
  const [params, setParams] = useSearchParams();
  const [closed, setClosed] = useState(false);
  const [starting, setStarting] = useState(false);
  const role = useQuery(roleQuery(project.id));
  const query = useQuery(pokerListQuery(project.id, closed));
  const requested = params.get('session');
  const sessionId =
    requested &&
    /^[1-9]\d*$/.test(requested) &&
    Number.isSafeInteger(Number(requested))
      ? Number(requested)
      : query.data?.[0]?.id;
  const firstId = query.data?.[0]?.id;
  useEffect(() => {
    if (requested || firstId === undefined) return;
    setParams(
      (previous) => {
        const next = new URLSearchParams(previous);
        next.set('session', String(firstId));
        return next;
      },
      { replace: true },
    );
  }, [requested, firstId, setParams]);
  const canWrite =
    !project.archivedAt &&
    ['Owner', 'Admin', 'Member'].includes(role.data?.role ?? '');
  const select = (id: number) => {
    const next = new URLSearchParams(params);
    next.set('session', String(id));
    setParams(next);
  };
  if (role.isError || query.isError)
    return (
      <ErrorState
        detail={t('failed')}
        onRetry={() => {
          void role.refetch();
          void query.refetch();
        }}
      />
    );
  if (role.isPending || query.isPending)
    return <Skeleton label={t('loading')} />;
  return (
    <>
      {!canWrite && <p className="poker-hint">{t('readonly')}</p>}
      <div className="poker-actions">
        <button
          className="secondary-action"
          aria-pressed={!closed}
          onClick={() => {
            setClosed(false);
            setParams({});
          }}
        >
          {t('open')}
        </button>
        <button
          className="secondary-action"
          aria-pressed={closed}
          onClick={() => {
            setClosed(true);
            setParams({});
          }}
        >
          {t('closed')}
        </button>
        {canWrite && (
          <button
            className="primary-action"
            aria-expanded={starting}
            onClick={() => setStarting(!starting)}
          >
            {t('newSession')}
          </button>
        )}
      </div>
      {canWrite && starting && (
        <StartSession
          projectId={project.id}
          onStarted={(id) => {
            setStarting(false);
            setClosed(false);
            select(id);
          }}
        />
      )}
      <div className="poker-layout">
        <div className="poker-panel poker-session-list">
          <h2>{t(closed ? 'closed' : 'open')}</h2>
          {!query.data.length && (
            <EmptyState
              title={t('noSessions')}
              description={t('noSessionsHint')}
            />
          )}
          <ul>
            {query.data.map((session) => (
              <li key={session.id}>
                <button
                  type="button"
                  aria-pressed={session.id === sessionId}
                  onClick={() => select(session.id)}
                >
                  <strong>{session.storyTitle}</strong>
                  <span>
                    {t(session.status)} ·{' '}
                    {t('round', { number: session.round })}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        </div>
        {sessionId && (
          <PokerRoom
            key={sessionId}
            projectId={project.id}
            sessionId={sessionId}
          />
        )}
      </div>
    </>
  );
}
