import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router';
import type { Project, User } from '../../api/contracts';
import { sprintsQuery } from '../../api/queries';
import { useT } from '../../shared/i18n/LanguageProvider';
import { PageHeader } from '../../shared/ui/ui';
import { EmptyState, ErrorState, Skeleton } from '../../shared/ui/States';
import { dashboardQuery } from './queries';
import { dashboardText } from './i18n';
import { DashboardEditor } from './DashboardEditor';
import { WidgetContent } from './WidgetContent';
export function DashboardView({
  project,
  user,
}: {
  project: Project | undefined;
  user: User;
}) {
  const t = useT(dashboardText);
  return (
    <section className="page dashboard-page">
      <PageHeader
        guide="dashboard"
        eyebrow={t('eyebrow')}
        title={t('title')}
        description={t('description')}
      />
      {project ? (
        <ProjectDashboard key={project.id} project={project} user={user} />
      ) : (
        <EmptyState title={t('chooseProject')} />
      )}
    </section>
  );
}
function ProjectDashboard({ project, user }: { project: Project; user: User }) {
  const t = useT(dashboardText);
  const query = useQuery(dashboardQuery(project.id));
  const sprints = useQuery(sprintsQuery(project.id));
  const [params, setParams] = useSearchParams();
  const [editing, setEditing] = useState(false);
  const [saved, setSaved] = useState(false);
  if (query.isError || sprints.isError)
    return (
      <ErrorState
        detail={t('layoutFailed')}
        onRetry={() => {
          void query.refetch();
          void sprints.refetch();
        }}
      />
    );
  if (!query.data || !sprints.data) return <Skeleton label={t('loading')} />;
  const requested = Number(params.get('sprint'));
  const sprint = requested
    ? sprints.data.find((s) => s.id === requested)
    : sprints.data.find((s) => s.status === 'Active');
  return (
    <>
      <div className="dashboard-actions">
        <label>
          {t('sprint')}
          <select
            value={requested || 0}
            onChange={(e) => {
              const next = new URLSearchParams(params);
              if (e.target.value === '0') next.delete('sprint');
              else next.set('sprint', e.target.value);
              setParams(next);
            }}
          >
            <option value="0">{t('active')}</option>
            {sprints.data.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
        </label>
        {!project.archivedAt && (
          <button
            className="primary-action"
            onClick={() => {
              setSaved(false);
              setEditing(true);
            }}
          >
            {t('edit')}
          </button>
        )}
      </div>
      {saved && <p role="status">{t('saved')}</p>}
      {project.archivedAt && <p>{t('readonly')}</p>}
      {query.data.widgets.length === 0 && (
        <EmptyState title={t('empty')} description={t('emptyHint')} />
      )}
      <div className="dashboard-grid">
        {query.data.widgets.map((widget) => (
          <section
            key={widget.type}
            aria-label={t(widget.type)}
            className={`dashboard-card${widget.wide ? ' dashboard-wide' : ''}`}
          >
            <h2>{t(widget.type)}</h2>
            <WidgetContent
              type={widget.type}
              projectId={project.id}
              user={user}
              sprint={sprint}
            />
          </section>
        ))}
      </div>
      {editing && !project.archivedAt && (
        <DashboardEditor
          projectId={project.id}
          initial={query.data}
          onClose={() => setEditing(false)}
          onSaved={() => setSaved(true)}
        />
      )}
    </>
  );
}
