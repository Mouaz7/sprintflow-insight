import { useT } from '../i18n/LanguageProvider';
import { Skeleton } from './States';
export function RouteLoading() {
  const t = useT();
  return (
    <div className="route-loading">
      <Skeleton label={t('loading')} />
    </div>
  );
}
