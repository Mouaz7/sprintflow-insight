import { useQuery } from '@tanstack/react-query';
import { useT } from '../../shared/i18n/LanguageProvider';
import { Notice } from '../../shared/ui/ui';
import { organizationsText } from './i18n';
import {
  organizationQuery,
  orgPath,
  useOrganizationWrite,
  type Organization,
} from './queries';

type Policy = { requireTwoFactor: boolean };

export function OrganizationSecurity({
  organization,
}: {
  organization: Organization;
}) {
  const t = useT(organizationsText);
  const query = useQuery(
    organizationQuery<Policy>(organization.slug, 'security'),
  );
  const update = useOrganizationWrite<Policy>(orgPath(organization.slug));
  return (
    <section className="analysis-card">
      <h2>{t('security')}</h2>
      <Notice error={query.error?.message ?? update.error?.message} />
      {query.isPending && <p role="status">{t('loading')}</p>}
      {query.data && (
        <>
          <label>
            <input
              type="checkbox"
              checked={query.data.requireTwoFactor}
              disabled={organization.role !== 'Owner' || update.isPending}
              onChange={(event) =>
                update.mutate({
                  path: '/security',
                  method: 'PUT',
                  body: { requireTwoFactor: event.target.checked },
                })
              }
            />
            {t('requireTwoFactor')}
          </label>
          <p>{t('twoFactorPolicyDescription')}</p>
        </>
      )}
    </section>
  );
}
