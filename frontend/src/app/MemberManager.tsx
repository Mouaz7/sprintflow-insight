import { FormGuide } from './Guidance';
import { localizeMessage, roleLabel } from './messages';
import { FormEvent, useEffect, useState } from 'react';
import { api } from '../api/client';
import type { Project, ProjectMember } from '../api/contracts';
import { useConfirm } from './ui';

export function MemberManager({
  project,
  userEmail,
  language,
  onChanged,
}: {
  project?: Project;
  userEmail: string;
  language: 'EN' | 'SV';
  onChanged: () => Promise<void>;
}) {
  const confirm = useConfirm();
  const sv = language === 'SV';
  const [members, setMembers] = useState<ProjectMember[]>([]);
  const [email, setEmail] = useState('');
  const [role, setRole] = useState<'Admin' | 'Member' | 'Viewer'>('Member');
  const [error, setError] = useState('');
  useEffect(() => {
    let cancelled = false;
    setMembers([]);
    setError('');
    if (!project)
      return () => {
        cancelled = true;
      };
    api<ProjectMember[]>(`/projects/${project.id}/members`)
      .then((result) => {
        if (!cancelled) setMembers(result);
      })
      .catch((cause: Error) => {
        if (!cancelled) setError(cause.message);
      });
    return () => {
      cancelled = true;
    };
  }, [project?.id]);
  if (
    !project ||
    (project.ownerEmail.toLowerCase() !== userEmail.toLowerCase() &&
      !members.some(
        (member) =>
          member.email.toLowerCase() === userEmail.toLowerCase() &&
          member.role === 'Admin',
      ))
  )
    return null;
  async function invite(event: FormEvent) {
    event.preventDefault();
    setError('');
    try {
      const saved = await api<ProjectMember>(
        `/projects/${project!.id}/members`,
        { method: 'POST', body: JSON.stringify({ email, role }) },
      );
      setMembers((items) => [
        ...items.filter(
          (item) => item.email.toLowerCase() !== saved.email.toLowerCase(),
        ),
        saved,
      ]);
      setEmail('');
    } catch (cause) {
      setError(
        cause instanceof Error ? cause.message : 'Could not update membership.',
      );
    }
  }
  async function remove(member: ProjectMember) {
    if (
      !(await confirm(
        sv
          ? `Ta bort ${member.email} från projektet?`
          : `Remove ${member.email} from this project?`,
      ))
    )
      return;
    try {
      await api<void>(
        `/projects/${project!.id}/members/${encodeURIComponent(member.email)}`,
        { method: 'DELETE' },
      );
      setMembers((items) => items.filter((item) => item.id !== member.id));
    } catch (cause) {
      setError(
        cause instanceof Error ? cause.message : 'Could not remove member.',
      );
    }
  }
  async function transfer(member: ProjectMember) {
    if (
      !(await confirm(
        sv
          ? `Överföra ägarskapet till ${member.email}?`
          : `Transfer ownership to ${member.email}?`,
      ))
    )
      return;
    try {
      await api<Project>(`/projects/${project!.id}/owner`, {
        method: 'PUT',
        body: JSON.stringify({ email: member.email }),
      });
      await onChanged();
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : 'Could not transfer ownership.',
      );
    }
  }
  return (
    <section className="member-manager">
      <div>
        <p className="kicker">{sv ? 'ÅTKOMST' : 'ACCESS'}</p>
        <h2>
          {sv ? `Medlemmar · ${project.name}` : `Members · ${project.name}`}
        </h2>
        <p>
          {sv
            ? 'Bjud in registrerade användare med administratörs-, arbets- eller läsbehörighet.'
            : 'Invite registered users as admins, contributors or read-only viewers.'}
        </p>
      </div>
      <FormGuide topic="member" language={language} />
      <form onSubmit={invite}>
        <label>
          {sv ? 'E-post' : 'Email'}
          <input
            required
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            placeholder="name@example.com"
          />
        </label>
        <label>
          {sv ? 'Roll' : 'Role'}
          <select
            aria-label={sv ? 'Roll' : 'Role'}
            value={role}
            onChange={(event) =>
              setRole(event.target.value as 'Admin' | 'Member' | 'Viewer')
            }
          >
            <option value="Admin">{roleLabel('Admin', language)}</option>
            <option value="Member">{roleLabel('Member', language)}</option>
            <option value="Viewer">{roleLabel('Viewer', language)}</option>
          </select>
        </label>
        <button className="primary-action">
          {sv ? 'Spara medlem' : 'Add member'}
        </button>
      </form>
      {error && (
        <p className="form-message error" role="alert">
          {localizeMessage(error, sv ? 'SV' : 'EN')}
        </p>
      )}
      <div className="member-list">
        {members.map((member) => (
          <article key={member.id}>
            <span>{member.email}</span>
            <b>{roleLabel(member.role, language)}</b>
            {member.role !== 'Owner' && (
              <>
                {project.ownerEmail.toLowerCase() === userEmail.toLowerCase() && (
                  <button type="button" onClick={() => transfer(member)}>
                    {sv ? 'Gör till ägare' : 'Make owner'}
                  </button>
                )}
                <button
                  type="button"
                  className="danger-action"
                  onClick={() => remove(member)}
                >
                  {sv ? 'Ta bort' : 'Remove'}
                </button>
              </>
            )}
          </article>
        ))}
      </div>
    </section>
  );
}
