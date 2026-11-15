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
import { PageHeader } from '../shared/ui/ui';

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
  language,
}: {
  view: InsightView;
  projectId?: number | undefined;
  hasProjects: boolean;
  language: 'EN' | 'SV';
}) {
  const sv = language === 'SV';
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
  const title = (
    sv
      ? {
          backlog: 'Backlog',
          sprints: 'Sprintar',
          metrics: 'Mätvärden',
          updates: 'Uppdateringar',
        }
      : {
          backlog: 'Backlog',
          sprints: 'Sprints',
          metrics: 'Metrics',
          updates: 'Updates',
        }
  )[view];
  const description = (
    sv
      ? {
          backlog: 'Idéer med en tydlig prioritet och nästa steg.',
          sprints: 'Ett gemensamt mål. En fokuserad leverans.',
          metrics: 'Förstå hur arbetet rör sig framåt.',
          updates: 'Projektets historia, ett steg i taget.',
        }
      : {
          backlog: 'Ideas with a clear priority and next step.',
          sprints: 'A shared goal. A focused delivery.',
          metrics: 'Understand how work moves forward.',
          updates: 'Your project’s story, one step at a time.',
        }
  )[view];
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
        language={language}
        eyebrow={
          sv
            ? 'ARBETSYTA / ' + title.toUpperCase()
            : 'WORKSPACE / ' + title.toUpperCase()
        }
        title={title}
        description={description}
      />
      {!hasProjects || !projectId ? (
        <div className="empty-state">
          {sv
            ? `Skapa ett projekt innan du använder ${title.toLowerCase()}.`
            : `Create a project before using ${title.toLowerCase()}.`}
        </div>
      ) : (
        <>
          {error && (
            <div className="load-error">
              <p className="form-message error" role="alert">
                {localizeMessage(error, sv ? 'SV' : 'EN')}
              </p>
              <button className="secondary-action" onClick={onChanged}>
                {sv ? 'Försök igen' : 'Try again'}
              </button>
            </div>
          )}
          {view === 'metrics' ? (
            <>
              <MetricsCards projectId={projectId} language={language} />
              <ShareReportCard projectId={projectId} language={language} />
            </>
          ) : loading ? (
            <p role="status" className="loading-state">
              {sv ? 'Laddar…' : 'Loading…'}
            </p>
          ) : view === 'backlog' ? (
            <BacklogManager
              key={view}
              projectId={projectId}
              items={data as Story[]}
              language={language}
              onChanged={(saved) => (saved ? merge(saved) : onChanged())}
            />
          ) : view === 'sprints' ? (
            <SprintManager
              key={view}
              projectId={projectId}
              items={data as Sprint[]}
              language={language}
              onChanged={(saved) => (saved ? merge(saved) : onChanged())}
            />
          ) : (
            <ActivityTimeline
              projectId={projectId}
              items={data as Activity[]}
              language={language}
              canManage={canManage}
              onChanged={onChanged}
            />
          )}
        </>
      )}
    </section>
  );
}
