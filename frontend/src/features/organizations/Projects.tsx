import { useQuery } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import type { Project } from '../../api/contracts';
import { useLanguage, useT } from '../../shared/i18n/LanguageProvider';
import { Drawer, Notice, useConfirm } from '../../shared/ui/ui';
import { organizationsText } from './i18n';
import {
  organizationQuery,
  orgPath,
  useOrganizationWrite,
  type Organization,
  type Starter,
  type StarterKind,
} from './queries';

const starterKinds: readonly StarterKind[] = ['Blank', 'Scrum', 'Kanban'];

export function Projects({
  organization,
  projects,
  onOpenProject,
  onProjectsChanged,
}: {
  organization: Organization;
  projects: Project[];
  onOpenProject: (id: number) => void;
  onProjectsChanged: () => Promise<void>;
}) {
  const t = useT(organizationsText);
  const [newProject, setNewProject] = useState(false);
  return (
    <section className="analysis-card">
      <h2>{t('projects')}</h2>
      {organization.role !== 'Viewer' && (
        <button className="primary-action" onClick={() => setNewProject(true)}>
          {t('newProject')}
        </button>
      )}
      {!projects.length && <p>{t('noProjects')}</p>}
      <ul className="organization-list">
        {projects.map((project) => (
          <li key={project.id}>
            <span>
              {project.name}
              {project.key && <small> · {project.key}</small>}
            </span>
            <button onClick={() => onOpenProject(project.id)}>
              {t('openProject')}
            </button>
          </li>
        ))}
      </ul>
      {newProject && (
        <ProjectForm
          organization={organization}
          onClose={() => setNewProject(false)}
          onProjectsChanged={onProjectsChanged}
        />
      )}
    </section>
  );
}

function ProjectForm({
  organization,
  template,
  onClose,
  onProjectsChanged,
}: {
  organization: Organization;
  template?: Starter;
  onClose: () => void;
  onProjectsChanged: () => Promise<void>;
}) {
  const t = useT(organizationsText);
  const { language } = useLanguage();
  const [name, setName] = useState('');
  const [description, setDescription] = useState(template?.description ?? '');
  const [key, setKey] = useState('');
  const action = useOrganizationWrite<Project>(
    orgPath(organization.slug),
    async () => {
      await onProjectsChanged();
      onClose();
    },
  );
  function submit(event: FormEvent) {
    event.preventDefault();
    action.mutate({
      path: template
        ? `/templates/${template.id}/projects?language=${language}`
        : '/projects',
      body: { name, description, ...(key.trim() && { key: key.trim() }) },
    });
  }
  return (
    <Drawer
      title={t(template ? 'useTemplate' : 'newProject')}
      onClose={onClose}
      dirty={!!name || !!key || description !== (template?.description ?? '')}
      busy={action.isPending}
    >
      <form className="editor-form" onSubmit={submit}>
        <label>
          {t('projectName')}
          <input
            required
            maxLength={255}
            value={name}
            onChange={(event) => setName(event.target.value)}
          />
        </label>
        <label>
          {t('description')}
          <textarea
            rows={3}
            maxLength={255}
            value={description}
            onChange={(event) => setDescription(event.target.value)}
          />
        </label>
        <label>
          {t('projectKey')}
          <input
            maxLength={10}
            pattern="[A-Za-z]{2,10}"
            value={key}
            onChange={(event) => setKey(event.target.value)}
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

export function Templates({
  organization,
  onProjectsChanged,
}: {
  organization: Organization;
  onProjectsChanged: () => Promise<void>;
}) {
  const t = useT(organizationsText);
  const query = useQuery(
    organizationQuery<Starter[]>(organization.slug, 'templates'),
  );
  const [newTemplate, setNewTemplate] = useState(false);
  const [selected, setSelected] = useState<Starter>();
  const remove = useOrganizationWrite(orgPath(organization.slug));
  const confirm = useConfirm();
  const manage = organization.role === 'Owner' || organization.role === 'Admin';
  return (
    <section className="analysis-card">
      <h2>{t('templates')}</h2>
      <Notice error={query.error?.message ?? remove.error?.message} />
      {query.isPending && <p role="status">{t('loading')}</p>}
      {manage && (
        <button className="primary-action" onClick={() => setNewTemplate(true)}>
          {t('createTemplate')}
        </button>
      )}
      {query.data?.length === 0 && <p>{t('noTemplates')}</p>}
      <ul className="organization-list">
        {query.data?.map((template) => (
          <li key={template.id}>
            <span>
              {template.name} · {t(template.kind)}
            </span>
            <div className="card-actions">
              {organization.role !== 'Viewer' && (
                <button onClick={() => setSelected(template)}>
                  {t('useTemplate')}
                </button>
              )}
              {manage && (
                <button
                  className="danger-action"
                  disabled={remove.isPending}
                  onClick={async () => {
                    if (await confirm(t('deleteTemplateConfirmation')))
                      remove.mutate({
                        path: `/templates/${template.id}`,
                        method: 'DELETE',
                      });
                  }}
                >
                  {t('delete')}
                </button>
              )}
            </div>
          </li>
        ))}
      </ul>
      {newTemplate && (
        <NewTemplate
          organization={organization}
          onClose={() => setNewTemplate(false)}
        />
      )}
      {selected && (
        <ProjectForm
          organization={organization}
          template={selected}
          onProjectsChanged={onProjectsChanged}
          onClose={() => setSelected(undefined)}
        />
      )}
    </section>
  );
}

function NewTemplate({
  organization,
  onClose,
}: {
  organization: Organization;
  onClose: () => void;
}) {
  const t = useT(organizationsText);
  const [name, setName] = useState('');
  const [kind, setKind] = useState<StarterKind>('Blank');
  const [description, setDescription] = useState('');
  const action = useOrganizationWrite(orgPath(organization.slug), onClose);
  function submit(event: FormEvent) {
    event.preventDefault();
    action.mutate({ path: '/templates', body: { name, kind, description } });
  }
  return (
    <Drawer
      title={t('createTemplate')}
      onClose={onClose}
      dirty={!!name || !!description || kind !== 'Blank'}
      busy={action.isPending}
    >
      <form className="editor-form" onSubmit={submit}>
        <label>
          {t('templateName')}
          <input
            required
            maxLength={120}
            value={name}
            onChange={(event) => setName(event.target.value)}
          />
        </label>
        <label>
          {t('templateKind')}
          <select
            value={kind}
            onChange={(event) => setKind(event.target.value as StarterKind)}
          >
            {starterKinds.map((item) => (
              <option key={item} value={item}>
                {t(item)}
              </option>
            ))}
          </select>
        </label>
        <label>
          {t('description')}
          <textarea
            rows={3}
            maxLength={255}
            value={description}
            onChange={(event) => setDescription(event.target.value)}
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
