import { useQuery } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { useLanguage, useT } from '../../shared/i18n/LanguageProvider';
import { Drawer, Notice, useConfirm } from '../../shared/ui/ui';
import { organizationsText } from './i18n';
import {
  organizationQuery,
  orgPath,
  orgRoute,
  useOrganizationWrite,
  type Invitation,
  type IssuedInvitation,
  type Member,
  type Organization,
  type OrganizationRole,
  type Team,
} from './queries';

const assignableRoles: readonly OrganizationRole[] = [
  'Admin',
  'Member',
  'Viewer',
];

export function Members({ organization }: { organization: Organization }) {
  const t = useT(organizationsText);
  const result = useQuery(
    organizationQuery<Member[]>(organization.slug, 'members'),
  );
  const change = useOrganizationWrite(orgPath(organization.slug));
  const confirm = useConfirm();
  const owner = organization.role === 'Owner';
  return (
    <section className="analysis-card">
      <h2>{t('members')}</h2>
      <Notice error={result.error?.message ?? change.error?.message} />
      {result.isPending && <p role="status">{t('loading')}</p>}
      <ul className="organization-list">
        {result.data?.map((member) => (
          <li key={member.userId}>
            <span>
              {member.name || member.email}
              {member.name && <small> · {member.email}</small>}
            </span>
            <span className="status-badge">{t(member.role)}</span>
            {member.role !== 'Owner' &&
              (owner ||
                (organization.role === 'Admin' && member.role !== 'Admin')) && (
                <div className="card-actions">
                  <RoleEditor
                    key={member.role}
                    role={member.role}
                    owner={owner}
                    busy={change.isPending}
                    onSave={(role) =>
                      change.mutate({
                        path: `/members/${member.userId}`,
                        method: 'PUT',
                        body: { role },
                      })
                    }
                  />
                  {owner &&
                    !organization.personal &&
                    member.role !== 'Viewer' && (
                      <button
                        disabled={change.isPending}
                        onClick={async () => {
                          if (await confirm(t('transferConfirmation')))
                            change.mutate({
                              path: '/ownership',
                              method: 'PUT',
                              body: { userId: member.userId },
                            });
                        }}
                      >
                        {t('transferOwnership')}
                      </button>
                    )}
                  <button
                    className="danger-action"
                    disabled={change.isPending}
                    onClick={async () => {
                      if (await confirm(t('removeMemberConfirmation')))
                        change.mutate({
                          path: `/members/${member.userId}`,
                          method: 'DELETE',
                        });
                    }}
                  >
                    {t('remove')}
                  </button>
                </div>
              )}
          </li>
        ))}
      </ul>
    </section>
  );
}

function RoleEditor({
  role,
  owner,
  busy,
  onSave,
}: {
  role: OrganizationRole;
  owner: boolean;
  busy: boolean;
  onSave: (role: OrganizationRole) => void;
}) {
  const t = useT(organizationsText);
  const [selected, select] = useState(role);
  return (
    <form
      className="card-actions"
      onSubmit={(event) => {
        event.preventDefault();
        onSave(selected);
      }}
    >
      <label>
        {t('role')}
        <select
          value={selected}
          onChange={(event) => select(event.target.value as OrganizationRole)}
        >
          {assignableRoles
            .filter((option) => owner || option !== 'Admin')
            .map((option) => (
              <option key={option} value={option}>
                {t(option)}
              </option>
            ))}
        </select>
      </label>
      <button disabled={busy || selected === role}>{t('saveRole')}</button>
    </form>
  );
}

export function Teams({ organization }: { organization: Organization }) {
  const t = useT(organizationsText);
  const teams = useQuery(organizationQuery<Team[]>(organization.slug, 'teams'));
  const [editing, edit] = useState<{ team?: Team }>();
  const [details, setDetails] = useState<Team>();
  const change = useOrganizationWrite(orgPath(organization.slug));
  const confirm = useConfirm();
  const manage = organization.role === 'Owner' || organization.role === 'Admin';
  return (
    <section className="analysis-card">
      <h2>{t('teams')}</h2>
      <Notice error={teams.error?.message ?? change.error?.message} />
      {teams.isPending && <p role="status">{t('loading')}</p>}
      {manage && (
        <button className="primary-action" onClick={() => edit({})}>
          {t('newTeam')}
        </button>
      )}
      {teams.data?.length === 0 && <p>{t('noTeams')}</p>}
      <ul className="organization-list">
        {teams.data?.map((team) => (
          <li key={team.id}>
            <span>
              {team.name} <small>· {team.memberCount}</small>
            </span>
            <div className="card-actions">
              <button onClick={() => setDetails(team)}>
                {t('teamMembers')}
              </button>
              {manage && (
                <>
                  <button onClick={() => edit({ team })}>
                    {t('renameTeam')}
                  </button>
                  <button
                    className="danger-action"
                    disabled={change.isPending}
                    onClick={async () => {
                      if (await confirm(t('deleteTeamConfirmation')))
                        change.mutate({
                          path: `/teams/${team.id}`,
                          method: 'DELETE',
                        });
                    }}
                  >
                    {t('delete')}
                  </button>
                </>
              )}
            </div>
          </li>
        ))}
      </ul>
      {editing && (
        <TeamForm
          organization={organization}
          team={editing.team}
          onClose={() => edit(undefined)}
        />
      )}
      {details && (
        <TeamMembers
          key={details.id}
          organization={organization}
          team={details}
          manage={manage}
          onClose={() => setDetails(undefined)}
        />
      )}
    </section>
  );
}

function TeamForm({
  organization,
  team,
  onClose,
}: {
  organization: Organization;
  team?: Team | undefined;
  onClose: () => void;
}) {
  const t = useT(organizationsText);
  const [name, setName] = useState(team?.name ?? '');
  const change = useOrganizationWrite(orgPath(organization.slug), onClose);
  function submit(event: FormEvent) {
    event.preventDefault();
    change.mutate({
      path: team ? `/teams/${team.id}` : '/teams',
      method: team ? 'PUT' : 'POST',
      body: { name },
    });
  }
  const title = t(team ? 'renameTeam' : 'newTeam');
  return (
    <Drawer
      title={title}
      onClose={onClose}
      busy={change.isPending}
      dirty={name !== (team?.name ?? '')}
    >
      <form className="editor-form" onSubmit={submit}>
        <label>
          {t('teamName')}
          <input
            required
            maxLength={120}
            value={name}
            onChange={(event) => setName(event.target.value)}
          />
        </label>
        <Notice error={change.error?.message} />
        <button className="primary-action" disabled={change.isPending}>
          {team ? title : t('create')}
        </button>
      </form>
    </Drawer>
  );
}

function TeamMembers({
  organization,
  team,
  manage,
  onClose,
}: {
  organization: Organization;
  team: Team;
  manage: boolean;
  onClose: () => void;
}) {
  const t = useT(organizationsText);
  const path = `teams/${team.id}/members`;
  const roster = useQuery(organizationQuery<Member[]>(organization.slug, path));
  const organizationMembers = useQuery({
    ...organizationQuery<Member[]>(organization.slug, 'members'),
    enabled: manage,
  });
  const [member, setMember] = useState('');
  const action = useOrganizationWrite(orgPath(organization.slug), () =>
    setMember(''),
  );
  const available =
    organizationMembers.data?.filter(
      (candidate) =>
        !roster.data?.some((current) => current.userId === candidate.userId),
    ) ?? [];
  return (
    <Drawer
      title={`${t('teamMembers')} · ${team.name}`}
      onClose={onClose}
      busy={action.isPending}
    >
      <Notice
        error={
          roster.error?.message ??
          organizationMembers.error?.message ??
          action.error?.message
        }
      />
      {roster.isPending && <p role="status">{t('loading')}</p>}
      {roster.data?.length === 0 && <p>{t('noMembers')}</p>}
      <ul className="organization-list">
        {roster.data?.map((person) => (
          <li key={person.userId}>
            <span>{person.name || person.email}</span>
            {manage && (
              <button
                className="danger-action"
                onClick={() =>
                  action.mutate({
                    path: `/${path}/${person.userId}`,
                    method: 'DELETE',
                  })
                }
              >
                {t('remove')}
              </button>
            )}
          </li>
        ))}
      </ul>
      {manage && !!available.length && (
        <form
          className="editor-form"
          onSubmit={(event) => {
            event.preventDefault();
            action.mutate({ path: `/${path}/${member}`, method: 'PUT' });
          }}
        >
          <label>
            {t('selectMember')}
            <select
              required
              value={member}
              onChange={(event) => setMember(event.target.value)}
            >
              <option value="">{t('selectMember')}</option>
              {available.map((person) => (
                <option key={person.userId} value={person.userId}>
                  {person.email}
                </option>
              ))}
            </select>
          </label>
          <button
            className="primary-action"
            disabled={action.isPending || !member}
          >
            {t('addMember')}
          </button>
        </form>
      )}
    </Drawer>
  );
}

export function Invitations({ organization }: { organization: Organization }) {
  const t = useT(organizationsText);
  const { format } = useLanguage();
  const list = useQuery(
    organizationQuery<Invitation[]>(organization.slug, 'invites'),
  );
  const [email, setEmail] = useState('');
  const [role, setRole] = useState<OrganizationRole>('Member');
  const [link, setLink] = useState('');
  const [copied, setCopied] = useState(false);
  const base = orgPath(organization.slug);
  const issue = useOrganizationWrite<IssuedInvitation>(base, ({ token }) => {
    setLink(
      `${window.location.origin}${orgRoute(organization.slug)}/invite#token=${encodeURIComponent(token)}`,
    );
    setEmail('');
    setCopied(false);
  });
  const revoke = useOrganizationWrite(base);
  const confirm = useConfirm();
  function submit(event: FormEvent) {
    event.preventDefault();
    issue.mutate({ path: '/invites', body: { email, role } });
  }
  return (
    <section className="analysis-card">
      <h2>{t('invitations')}</h2>
      <Notice
        error={
          list.error?.message ?? issue.error?.message ?? revoke.error?.message
        }
      />
      {list.isPending && <p role="status">{t('loading')}</p>}
      <form className="editor-form" onSubmit={submit}>
        <label>
          {t('inviteEmail')}
          <input
            type="email"
            required
            maxLength={255}
            value={email}
            onChange={(event) => setEmail(event.target.value)}
          />
        </label>
        <label>
          {t('role')}
          <select
            value={role}
            onChange={(event) =>
              setRole(event.target.value as OrganizationRole)
            }
          >
            {assignableRoles
              .filter(
                (option) => organization.role === 'Owner' || option !== 'Admin',
              )
              .map((option) => (
                <option key={option} value={option}>
                  {t(option)}
                </option>
              ))}
          </select>
        </label>
        <button className="primary-action" disabled={issue.isPending}>
          {t('createInvite')}
        </button>
      </form>
      {link && (
        <div className="editor-form">
          <label>
            {t('invitationLink')}
            <input
              readOnly
              value={link}
              onFocus={(event) => event.target.select()}
            />
          </label>
          {navigator.clipboard && (
            <button
              type="button"
              onClick={() => {
                void navigator.clipboard
                  .writeText(link)
                  .then(() => setCopied(true))
                  .catch(() => setCopied(false));
              }}
            >
              {t('copyLink')}
            </button>
          )}
          {copied && <p role="status">{t('copied')}</p>}
        </div>
      )}
      {list.data?.length === 0 && <p>{t('noInvites')}</p>}
      <ul className="organization-list">
        {list.data?.map((invitation) => (
          <li key={invitation.id}>
            <span>
              {invitation.email} · {t(invitation.role)}
            </span>
            <small>
              {t('expires')} {format.dateTime(invitation.expiresAt)}
            </small>
            <button
              disabled={revoke.isPending}
              onClick={async () => {
                if (await confirm(t('revokeConfirmation'))) {
                  setLink('');
                  revoke.mutate({
                    path: `/invites/${invitation.id}`,
                    method: 'DELETE',
                  });
                }
              }}
            >
              {t('revoke')}
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}
