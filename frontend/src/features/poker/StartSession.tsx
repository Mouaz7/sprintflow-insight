import { useId, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { storiesQuery } from '../../api/queries';
import { useT } from '../../shared/i18n/LanguageProvider';
import { ErrorState, Skeleton } from '../../shared/ui/States';
import type { PokerDeck } from './contracts';
import { pokerText } from './i18n';
import { useStartPoker } from './queries';

export function StartSession({
  projectId,
  onStarted,
}: {
  projectId: number;
  onStarted: (id: number) => void;
}) {
  const t = useT(pokerText);
  const query = useQuery(storiesQuery(projectId));
  const start = useStartPoker(projectId);
  const [storyId, setStoryId] = useState('');
  const [deck, setDeck] = useState<PokerDeck>('FIBONACCI');
  const [cards, setCards] = useState('1, 2, 3, 5, 8, ?');
  const [invalid, setInvalid] = useState(false);
  const hintId = useId();
  if (query.isError)
    return (
      <ErrorState detail={t('failed')} onRetry={() => void query.refetch()} />
    );
  if (query.isPending) return <Skeleton label={t('loading')} />;
  if (!query.data.length)
    return (
      <p>
        {t('noStories')} <Link to="/backlog">{t('backlog')}</Link>
      </p>
    );
  return (
    <form
      className="poker-panel poker-start"
      onSubmit={(event) => {
        event.preventDefault();
        const parsed = cards.split(',').map((card) => card.trim());
        if (
          deck === 'CUSTOM' &&
          (parsed.length < 2 ||
            parsed.length > 20 ||
            new Set(parsed).size !== parsed.length ||
            parsed.some(
              (card) =>
                card.length > 8 || !/^[\p{L}\p{N}\p{So}?.+-]{1,8}$/u.test(card),
            ) ||
            parsed.every((card) => ['?', '☕'].includes(card)))
        ) {
          setInvalid(true);
          return;
        }
        setInvalid(false);
        start.mutate(
          {
            storyId: Number(storyId),
            deck,
            ...(deck === 'CUSTOM' ? { cards: parsed } : {}),
          },
          { onSuccess: (saved) => onStarted(saved.id) },
        );
      }}
    >
      <h2>{t('newSession')}</h2>
      <fieldset disabled={start.isPending}>
        <label>
          {t('story')}
          <select
            required
            value={storyId}
            onChange={(event) => setStoryId(event.target.value)}
          >
            <option value="">{t('chooseStory')}</option>
            {query.data.map((story) => (
              <option key={story.id} value={story.id}>
                {story.title}
              </option>
            ))}
          </select>
        </label>
        <label>
          {t('deck')}
          <select
            value={deck}
            onChange={(event) => setDeck(event.target.value as PokerDeck)}
          >
            {(['FIBONACCI', 'TSHIRT', 'CUSTOM'] as const).map((value) => (
              <option key={value} value={value}>
                {t(value)}
              </option>
            ))}
          </select>
        </label>
        <p className="poker-hint">{t('deckHint')}</p>
        {deck === 'CUSTOM' && (
          <>
            <label>
              {t('cards')}
              <input
                required
                value={cards}
                maxLength={200}
                aria-describedby={hintId}
                aria-invalid={invalid || undefined}
                onChange={(event) => {
                  setCards(event.target.value);
                  setInvalid(false);
                }}
              />
            </label>
            <p className="poker-hint" id={hintId}>
              {t('cardsHint')}
            </p>
            {invalid && <p role="alert">{t('customInvalid')}</p>}
          </>
        )}
        <button className="primary-action" type="submit">
          {start.isPending ? t('pending') : t('start')}
        </button>
      </fieldset>
      {start.isError && <p role="alert">{t('writeFailed')}</p>}
    </form>
  );
}
