import { useQuery } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router';
import type { Project } from '../../api/contracts';
import { useT } from '../../shared/i18n/LanguageProvider';
import { Drawer, Notice, PageHeader } from '../../shared/ui/ui';
import { organizationsText } from './i18n';
import { OrganizationSecurity } from './OrganizationSecurity';
import { Invitations, Members, Teams } from './People';
import { Projects, Templates } from './Projects';
import {
  organizationQuery,
  organizationsQuery,
  orgPath,
  orgRoute,
  useOrganizationWrite,
  type Organization,
} from './queries';
import './organizations.css';

const tabs = [
  'projects',
  'members',
  'teams',
  'templates',
  'invitations',
  'security',
] as const;

export default function OrganizationView({
  onProjectsChanged,
  onOpenProject,
}: {
  onProjectsChanged: () => Promise<void>;
  onOpenProject: (id: number) => void;
}) {
  const t = useT(organizationsText);
  const { org, section } = useParams();
  const organizations = useQuery(organizationsQuery);
  const navigate = useNavigate();
  const current = organizations.data?.find(
    (organization) => organization.slug === org,
  );
  const [create, setCreate] = useState(false);
  const manage = current?.role === 'Owner' || current?.role === 'Admin';
  if (org && section === 'invite') return <AcceptInvitation slug={org} />;

  const title = current
    ? current.personal
      ? t('personal')
      : current.name
    : t('organizations');
  const activeTab = tabs.find((item) => item === section) ?? 'projects';
  return (
    <section className="page organizations-page">
      <PageHeader
        title={title}
        eyebrow={current ? t('organizations') : ''}
        action={
          !org && (
            <button className="primary-action" onClick={() => setCreate(true)}>
              {t('newOrganization')}
            </button>
          )
        }
      />
      <Notice error={organizations.error?.message} />
      {organizations.isPending && <p role="status">{t('loading')}</p>}
      {!!organizations.data?.length && (
        <label className="organization-picker">
          {t('chooseOrganization')}
          <select
            value={current?.slug ?? ''}
            onChange={(event) => {
              const slug = event.target.value;
              void navigate(slug ? orgRoute(slug) : '/organizations');
            }}
          >
            <option value="">{t('organizations')}</option>
            {organizations.data.map((organization) => (
              <option key={organization.slug} value={organization.slug}>
                {organization.personal ? t('personal') : organization.name}
              </option>
            ))}
          </select>
        </label>
      )}
      {!org && organizations.data && (
        <ul className="organization-list">
          {organizations.data.map((organization) => (
            <li key={organization.slug}>
              <Link to={orgRoute(organization.slug)}>
                {organization.personal ? t('personal') : organization.name}
              </Link>
              <span className="status-badge">{t(organization.role)}</span>
            </li>
          ))}
        </ul>
      )}
      {org && !current && organizations.isSuccess && (
        <Notice error={t('missing')} />
      )}
      {current && (
        <>
          <nav
            className="organization-tabs"
            aria-label={t('organizationNavigation')}
          >
            {tabs
              .filter(
                (item) =>
                  manage || (item !== 'invitations' && item !== 'security'),
              )
              .map((item) => (
                <Link
                  key={item}
                  to={`${orgRoute(current.slug)}/${item}`}
                  aria-current={activeTab === item ? 'page' : undefined}
                  className={activeTab === item ? 'active' : ''}
                >
                  {t(item)}
                </Link>
              ))}
          </nav>
          {activeTab === 'projects' && (
            <OrganizationProjects
              organization={current}
              onProjectsChanged={onProjectsChanged}
              onOpenProject={onOpenProject}
            />
          )}
          {activeTab === 'members' && (
            <Members key={current.slug} organization={current} />
          )}
          {activeTab === 'teams' && (
            <Teams key={current.slug} organization={current} />
          )}
          {activeTab === 'templates' && (
            <Templates
              key={current.slug}
              organization={current}
              onProjectsChanged={onProjectsChanged}
            />
          )}
          {activeTab === 'security' && manage && (
            <OrganizationSecurity key={current.slug} organization={current} />
          )}
          {activeTab === 'invitations' && manage && (
            <Invitations key={current.slug} organization={current} />
          )}
        </>
      )}
      {create && (
        <NewOrganization
          onClose={() => setCreate(false)}
          onCreated={(organization) => {
            setCreate(false);
            void navigate(orgRoute(organization.slug));
          }}
        />
      )}
    </section>
  );
}

function OrganizationProjects({
  organization,
  onProjectsChanged,
  onOpenProject,
}: {
  organization: Organization;
  onProjectsChanged: () => Promise<void>;
  onOpenProject: (id: number) => void;
}) {
  const t = useT(organizationsText);
  const result = useQuery(
    organizationQuery<Project[]>(organization.slug, 'projects'),
  );
  return (
    <>
      <Notice error={result.error?.message} />
      {result.isPending && <p role="status">{t('loading')}</p>}
      {result.data && (
        <Projects
          key={organization.slug}
          organization={organization}
          projects={result.data}
          onOpenProject={onOpenProject}
          onProjectsChanged={onProjectsChanged}
        />
      )}
    </>
  );
}

function NewOrganization({
  onClose,
  onCreated,
}: {
  onClose: () => void;
  onCreated: (organization: Organization) => void;
}) {
  const t = useT(organizationsText);
  const [name, setName] = useState('');
  const [slug, setSlug] = useState<string>();
  const suggested = name
    .toLowerCase()
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 80)
    .replace(/-$/g, '');
  const action = useOrganizationWrite<Organization>(
    '/organizations',
    onCreated,
  );
  function submit(event: FormEvent) {
    event.preventDefault();
    action.mutate({ body: { name, slug: slug ?? suggested } });
  }
  return (
    <Drawer
      title={t('newOrganization')}
      onClose={onClose}
      busy={action.isPending}
      dirty={!!name || !!slug}
    >
      <form className="editor-form" onSubmit={submit}>
        <label>
          {t('organizationName')}
          <input
            required
            maxLength={120}
            value={name}
            onChange={(event) => setName(event.target.value)}
          />
        </label>
        <label>
          {t('slug')}
          <input
            required
            minLength={3}
            maxLength={80}
            pattern="[a-z0-9][a-z0-9\-]{1,78}[a-z0-9]"
            value={slug ?? suggested}
            onChange={(event) => setSlug(event.target.value)}
          />
        </label>
        <Notice error={action.error?.message} />
        <button className="primary-action" disabled={action.isPending}>
          {t('create')}
        </button>
      </form>
    </Drawer>
  );
}

function AcceptInvitation({ slug }: { slug: string }) {
  const t = useT(organizationsText);
  const navigate = useNavigate();
  const { hash } = useLocation();
  const token = new URLSearchParams(hash.slice(1)).get('token');
  const action = useOrganizationWrite(orgPath(slug), () => {
    void navigate(orgRoute(slug), { replace: true });
  });
  return (
    <section className="page">
      <PageHeader eyebrow={t('organizations')} title={t('invitation')} />
      <p>{t('inviteExplanation')}</p>
      <Notice
        error={
          action.error?.message ?? (!token ? t('invalidInvitation') : undefined)
        }
      />
      <button
        className="primary-action"
        type="button"
        disabled={!token || action.isPending}
        onClick={() =>
          action.mutate({ path: '/invites/accept', body: { token } })
        }
      >
        {t('acceptInvite')}
      </button>
    </section>
  );
}
