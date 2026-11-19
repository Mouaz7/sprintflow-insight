import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useT } from '../../shared/i18n/LanguageProvider';
import { ErrorState, Skeleton } from '../../shared/ui/States';
import { useConfirm } from '../../shared/ui/ui';
import type { PokerCommand, PokerSession } from './contracts';
import { pokerText } from './i18n';
import { pokerSessionQuery, usePokerCommand } from './queries';
import { RoundResults } from './RoundResults';

export function PokerRoom({
  projectId,
  sessionId,
}: {
  projectId: number;
  sessionId: number;
}) {
  const t = useT(pokerText);
  const query = useQuery(pokerSessionQuery(projectId, sessionId));
  if (query.isError)
    return (
      <ErrorState detail={t('failed')} onRetry={() => void query.refetch()} />
    );
  if (query.isPending) return <Skeleton label={t('loading')} />;
  return <SessionRoom data={query.data} refresh={() => void query.refetch()} />;
}

function SessionRoom({
  data,
  refresh,
}: {
  data: PokerSession;
  refresh: () => void;
}) {
  const t = useT(pokerText);
  const confirm = useConfirm();
  const command = usePokerCommand(data.projectId, data.id);
  const current = data.rounds.find((round) => round.number === data.round);
  const previous = data.rounds.filter((round) => round.number < data.round);
  const run = (action: PokerCommand['action']) => {
    if (action === 'vote' || action === 'accept') return;
    command.mutate({ action, round: data.round });
  };
  return (
    <section className="poker-panel poker-room" aria-label={t('selected')}>
      <div className="poker-room-heading">
        <h2>{data.storyTitle}</h2>
        <span className="status-badge">{t(data.status)}</span>
      </div>
      <p>{t('currentPoints', { points: data.storyPoints })}</p>
      <p role="status">
        {t('round', { number: data.round })} ·{' '}
        {t('voters', { count: data.voters.length })}
      </p>
      {data.status === 'VOTING' && (
        <>
          <p className="poker-hint">{t('hidden')}</p>
          {data.canVote && (
            <fieldset disabled={command.isPending}>
              <legend>{t('chooseCard')}</legend>
              <div className="poker-cards">
                {data.cards.map((card) => (
                  <button
                    type="button"
                    key={card}
                    className="poker-card"
                    aria-pressed={data.myCard === card}
                    aria-label={t('cardLabel', {
                      card:
                        card === '?'
                          ? t('unsure')
                          : card === '☕'
                            ? t('break')
                            : card,
                    })}
                    onClick={() =>
                      command.mutate({
                        action: 'vote',
                        round: data.round,
                        card,
                      })
                    }
                  >
                    {card}
                  </button>
                ))}
              </div>
              {data.myCard !== null && (
                <>
                  <p>{t('yourCard', { card: data.myCard })}</p>
                  <button
                    className="secondary-action"
                    type="button"
                    onClick={() => run('retract')}
                  >
                    {t('retract')}
                  </button>
                </>
              )}
            </fieldset>
          )}
          {data.voters.length ? (
            <ul className="poker-voters">
              {data.voters.map((voter) => (
                <li key={voter.userId}>
                  {voter.name || t('anonymous')}{' '}
                  <span aria-hidden="true">✓</span>
                </li>
              ))}
            </ul>
          ) : (
            <p>{t('waiting')}</p>
          )}
        </>
      )}
      {current && <RoundResults round={current} />}
      {data.status === 'ACCEPTED' && data.finalStoryPoints !== null && (
        <p role="status">{t('saved', { points: data.finalStoryPoints })}</p>
      )}
      {data.canFacilitate && (
        <>
          <div className="poker-actions">
            {data.status === 'VOTING' && (
              <button
                className="primary-action"
                disabled={command.isPending || !data.voters.length}
                onClick={() => run('reveal')}
              >
                {t('reveal')}
              </button>
            )}
            {data.status === 'REVEALED' && (
              <button
                className="secondary-action"
                disabled={command.isPending || data.round >= 50}
                onClick={() => run('revote')}
              >
                {t('revote')}
              </button>
            )}
            <button
              className="secondary-action"
              disabled={command.isPending}
              onClick={() => {
                void confirm(t('cancelConfirm')).then((yes) => {
                  if (yes) run('cancel');
                });
              }}
            >
              {t('cancel')}
            </button>
          </div>
          {data.round >= 50 && data.status === 'REVEALED' && (
            <p>{t('maxRounds')}</p>
          )}
          {data.status === 'REVEALED' && current?.statistics.suggestion && (
            <Consensus
              key={data.round}
              data={data}
              suggested={current.statistics.suggestedStoryPoints}
              pending={command.isPending}
              onAccept={(storyPoints, storyVersion) =>
                command.mutate({
                  action: 'accept',
                  round: data.round,
                  storyPoints,
                  storyVersion,
                })
              }
            />
          )}
        </>
      )}
      {['VOTING', 'REVEALED'].includes(data.status) && (
        <p className="poker-hint">{t('facilitatorHint')}</p>
      )}
      {command.isError && <p role="alert">{t('writeFailed')}</p>}
      <button
        className="text-action"
        type="button"
        disabled={command.isPending}
        onClick={refresh}
      >
        {t('refresh')}
      </button>
      {previous.length > 0 && (
        <details className="poker-history">
          <summary>
            {t('history')} ({previous.length})
          </summary>
          {previous.map((round) => (
            <RoundResults key={round.number} round={round} />
          ))}
        </details>
      )}
    </section>
  );
}

function Consensus({
  data,
  suggested,
  pending,
  onAccept,
}: {
  data: PokerSession;
  suggested: number | null;
  pending: boolean;
  onAccept: (points: number, version: number) => void;
}) {
  const t = useT(pokerText);
  const [points, setPoints] = useState(suggested ?? data.storyPoints);
  const [version, setVersion] = useState(data.storyVersion);
  const changed = data.storyVersion !== version;
  return (
    <form
      className="poker-consensus"
      onSubmit={(event) => {
        event.preventDefault();
        if (!changed) onAccept(points, version);
      }}
    >
      <label>
        {t('estimate')}
        <select
          value={points}
          disabled={pending}
          onChange={(event) => setPoints(Number(event.target.value))}
        >
          {[1, 2, 3, 5, 8].map((point) => (
            <option key={point} value={point}>
              {point}
            </option>
          ))}
        </select>
      </label>
      <p className="poker-hint">{t('saveHint')}</p>
      {changed && (
        <div role="alert">
          <p>{t('storyChanged')}</p>
          <button
            className="secondary-action"
            type="button"
            onClick={() => {
              setVersion(data.storyVersion);
              setPoints(data.storyPoints);
            }}
          >
            {t('reloadEstimate')}
          </button>
        </div>
      )}
      <button
        className="primary-action"
        disabled={pending || changed}
        type="submit"
      >
        {pending ? t('pending') : t('save')}
      </button>
    </form>
  );
}
