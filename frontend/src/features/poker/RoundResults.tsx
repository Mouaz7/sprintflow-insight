import { useT } from '../../shared/i18n/LanguageProvider';
import type { PokerRound } from './contracts';
import { pokerText } from './i18n';

export function RoundResults({ round }: { round: PokerRound }) {
  const t = useT(pokerText);
  const stats = round.statistics;
  return (
    <section
      className="poker-results"
      aria-label={t('round', { number: round.number })}
    >
      <h3>
        {t('round', { number: round.number })} · {t('stats')}
      </h3>
      <p>
        {t(
          !stats.suggestion
            ? 'noEstimates'
            : stats.consensus
              ? 'consensus'
              : 'disagreement',
        )}
      </p>
      {stats.suggestion && stats.revoteRecommended && (
        <p>{t('recommendRevote')}</p>
      )}
      <ul className="poker-voters">
        {round.votes.map((vote) => (
          <li key={vote.userId}>
            <span>{vote.name || t('anonymous')}</span>
            <strong>{vote.card}</strong>
          </li>
        ))}
      </ul>
      <table className="poker-distribution">
        <caption>{t('distribution')}</caption>
        <thead>
          <tr>
            <th scope="col">{t('card')}</th>
            <th scope="col">{t('count')}</th>
          </tr>
        </thead>
        <tbody>
          {stats.distribution.map((row) => (
            <tr key={row.card}>
              <th scope="row">{row.card}</th>
              <td>{row.count}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <dl className="poker-statistics">
        <div>
          <dt>{t('min')}</dt>
          <dd>{stats.min ?? '—'}</dd>
        </div>
        <div>
          <dt>{t('max')}</dt>
          <dd>{stats.max ?? '—'}</dd>
        </div>
        <div>
          <dt>{t('median')}</dt>
          <dd>{stats.median ?? '—'}</dd>
        </div>
        {stats.mean !== null && (
          <div>
            <dt>{t('mean')}</dt>
            <dd>{t('number', { value: stats.mean })}</dd>
          </div>
        )}
        <div>
          <dt>{t('agreement')}</dt>
          <dd>{t('percent', { value: stats.agreement })}</dd>
        </div>
        <div>
          <dt>{t('abstentions')}</dt>
          <dd>{stats.abstentions}</dd>
        </div>
      </dl>
      <p className="poker-hint">{t('statsHint')}</p>
    </section>
  );
}
