import { localizeMessage } from './messages';
import { useEffect, useState } from 'react';
import { api } from '../api/client';
import type { Story, Sprint, Activity } from '../api/contracts';
import { PageHeader } from './ui';
import { ShareReportCard } from './SharedReports';
import { MetricsCards } from './MetricsView';
import { BacklogManager } from './BacklogView';
import { SprintManager } from './SprintView';
import { ActivityTimeline } from './ActivityTimeline';

type InsightView = 'backlog' | 'sprints' | 'metrics' | 'updates';
export function ProjectInsightView({
  view,
  projectId,
  hasProjects,
  language,
}: {
  view: InsightView;
  projectId?: number;
  hasProjects: boolean;
  language: 'EN' | 'SV';
}) {
  const sv = language === 'SV';
  const [data, setData] = useState<Story[] | Sprint[] | Activity[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [refresh, setRefresh] = useState(0);
  const [canManage, setCanManage] = useState(false);
  useEffect(() => {
    let cancelled = false;
    setData([]);
    setError('');
    setLoading(true);
    if (!projectId || view === 'metrics') {
      setLoading(false);
      return () => {
        cancelled = true;
      };
    }
    const path =
      view === 'backlog'
        ? `/stories/project/${projectId}`
        : view === 'sprints'
          ? `/sprints?projectId=${projectId}`
          : `/projects/${projectId}/activity`;
    api<Story[] | Sprint[] | Activity[]>(path)
      .then((result) => {
        if (!cancelled) setData(result);
      })
      .catch((cause) => {
        if (!cancelled) setError(cause.message);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [projectId, view, refresh]);
  useEffect(() => {
    let cancelled = false;
    setCanManage(false);
    if (projectId && view === 'updates')
      api<{ role: string }>(`/projects/${projectId}/role`)
        .then(({ role }) => {
          if (!cancelled) setCanManage(role === 'Owner' || role === 'Admin');
        })
        .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [projectId, view]);
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
  const onChanged = () => setRefresh((n) => n + 1);
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
              onChanged={(saved) => {
                if (!saved) return onChanged();
                setError('');
                setData((current) => {
                  const items = current as Story[];
                  return items.some((item) => item.id === saved.id)
                    ? items.map((item) => item.id === saved.id ? saved : item)
                    : [...items, saved];
                });
              }}
            />
          ) : view === 'sprints' ? (
            <SprintManager
              key={view}
              projectId={projectId}
              items={data as Sprint[]}
              language={language}
              onChanged={(saved) => {
                if (!saved) return onChanged();
                setError('');
                setData((current) => {
                  const items = current as Sprint[];
                  return items.some((item) => item.id === saved.id)
                    ? items.map((item) => item.id === saved.id ? saved : item)
                    : [...items, saved];
                });
              }}
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
