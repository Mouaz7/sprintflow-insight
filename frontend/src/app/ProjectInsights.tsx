import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import type { Activity, Sprint, Story } from '../api/contracts';
import { afterProjectWrite, queryKeys, roleQuery } from '../api/queries';
import { ActivityTimeline } from '../features/insights/ActivityTimeline';
import { MetricsCards } from '../features/insights/MetricsView';
import { activityQuery } from '../features/insights/queries';
import { ShareReportCard } from '../features/insights/SharedReports';
import { BacklogManager } from '../features/planning/BacklogView';
import { SprintManager } from '../features/planning/SprintView';
import { localizeMessage } from '../shared/i18n/messages';
import { useLanguage, useT } from '../shared/i18n/LanguageProvider';
import { PageHeader } from '../shared/ui/ui';
import { appText } from './i18n';

type InsightView = 'backlog' | 'sprints' | 'metrics' | 'updates';

type InsightList = Story[] | Sprint[] | Activity[];

/** The list shown by an insight view (same keys as other views); metrics loads its own data. */
function insightQuery(view: InsightView, projectId: number) {
  const path =
    view === 'backlog'
      ? `/stories/project/${projectId}`
      : view === 'sprints'
        ? `/sprints?projectId=${projectId}`
        : `/projects/${projectId}/activity`;
  if (view === 'metrics') return undefined;
  return {
    queryKey:
      view === 'backlog'
        ? queryKeys.stories(projectId)
        : view === 'sprints'
          ? queryKeys.sprints(projectId)
          : activityQuery(projectId).queryKey,
    queryFn: () => api<InsightList>(path),
  };
}

export function ProjectInsightView({
  view,
  projectId,
  hasProjects,
}: {
  view: InsightView;
  projectId?: number | undefined;
  hasProjects: boolean;
}) {
  const t = useT(appText);
  const { language } = useLanguage();
  const queryClient = useQueryClient();
  const listQuery = projectId ? insightQuery(view, projectId) : undefined;
  const list = useQuery<InsightList>({
    queryKey: listQuery?.queryKey ?? ['project', 0, view],
    queryFn: listQuery?.queryFn ?? (() => Promise.resolve([])),
    enabled: !!listQuery,
  });
  const role = useQuery({
    ...roleQuery(projectId ?? 0),
    enabled: !!projectId && view === 'updates',
  });
  const canManage = role.data?.role === 'Owner' || role.data?.role === 'Admin';
  const data = list.data ?? [];
  const loading = !!listQuery && list.isPending;
  const error = list.error?.message ?? '';
  const title = t(view);
  const description = t(`${view}Description`);
  const onChanged = () => {
    if (!projectId || !listQuery) return;
    void afterProjectWrite(queryClient, projectId, listQuery.queryKey);
  };
  function merge<T extends { id: number }>(saved: T) {
    if (!projectId || !listQuery) return;
    queryClient.setQueryData<T[]>(listQuery.queryKey, (current = []) =>
      current.some((item) => item.id === saved.id)
        ? current.map((item) => (item.id === saved.id ? saved : item))
        : [...current, saved],
    );
    void afterProjectWrite(queryClient, projectId);
  }
  return (
    <section className={'page ' + view + '-page'}>
      <PageHeader
        guide={view}
        eyebrow={t('workspaceEyebrow', { title })}
        title={title}
        description={description}
      />
      {!hasProjects || !projectId ? (
        <div className="empty-state">{t('createProjectFirst', { title })}</div>
      ) : (
        <>
          {error && (
            <div className="load-error">
              <p className="form-message error" role="alert">
                {localizeMessage(error, language)}
              </p>
              <button className="secondary-action" onClick={onChanged}>
                {t('tryAgain')}
              </button>
            </div>
          )}
          {view === 'metrics' ? (
            <>
              <MetricsCards projectId={projectId} />
              <ShareReportCard projectId={projectId} />
            </>
          ) : loading ? (
            <p role="status" className="loading-state">
              {t('loading')}
            </p>
          ) : view === 'backlog' ? (
            <BacklogManager
              key={view}
              projectId={projectId}
              items={data as Story[]}

              onChanged={(saved) => (saved ? merge(saved) : onChanged())}
            />
          ) : view === 'sprints' ? (
            <SprintManager
              key={view}
              projectId={projectId}
              items={data as Sprint[]}

              onChanged={(saved) => (saved ? merge(saved) : onChanged())}
            />
          ) : (
            <ActivityTimeline
              projectId={projectId}
              items={data as Activity[]}

              canManage={canManage}
              onChanged={onChanged}
            />
          )}
        </>
      )}
    </section>
  );
}
