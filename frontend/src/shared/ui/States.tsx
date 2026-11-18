import type { ReactNode } from 'react';
import { useT } from '../i18n/LanguageProvider';
import { defineLexicon } from '../i18n/lexicon';

const stateText = defineLexicon({
  EN: { retry: 'Try again', error: 'Could not load this content' },
  SV: { retry: 'Försök igen', error: 'Innehållet kunde inte hämtas' },
});

export function EmptyState({
  title,
  description,
  action,
}: {
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className="empty-state" role="status">
      <h2>{title}</h2>
      {description && <p>{description}</p>}
      {action}
    </div>
  );
}

export function Skeleton({
  label,
  lines = 3,
}: {
  label?: string;
  lines?: number;
}) {
  const t = useT();
  const count = Math.max(1, Math.min(8, Math.floor(lines)));
  return (
    <div className="skeleton" role="status" aria-busy="true">
      <p>{label ?? t('loading')}</p>
      <div aria-hidden="true">
        {Array.from({ length: count }, (_, i) => (
          <span key={i} className="skeleton-line" />
        ))}
      </div>
    </div>
  );
}

export function ErrorState({
  title,
  detail,
  onRetry,
}: {
  title?: string;
  detail: string;
  onRetry?: () => void;
}) {
  const t = useT(stateText);
  return (
    <section className="error-state" role="alert">
      <h2>{title ?? t('error')}</h2>
      <p>{detail}</p>
      {onRetry && (
        <button type="button" className="secondary-action" onClick={onRetry}>
          {t('retry')}
        </button>
      )}
    </section>
  );
}
