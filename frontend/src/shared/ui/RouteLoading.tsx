import { useT } from '../i18n/LanguageProvider';

export function RouteLoading() {
  const t = useT();
  return (
    <p className="route-loading" role="status">
      {t('loading')}
    </p>
  );
}
