import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';
import { roleQuery } from '../../api/queries';
import type { Language } from '../../api/contracts';
import {
  LanguageScope,
  useLanguage,
  useT,
} from '../../shared/i18n/LanguageProvider';
import { storedLanguage } from '../../shared/lib/appearance';
import { Notice, useConfirm } from '../../shared/ui/ui';
import { flowStatus, insightsText } from './i18n';
import { publicReportQuery, reportsQuery, type SharedReport } from './queries';

export function ShareReportCard({ projectId }: { projectId: number }) {
  const t = useT(insightsText);
  const { format } = useLanguage();
  const queryClient = useQueryClient();
  const roleResult = useQuery(roleQuery(projectId));
  const role = roleResult.data?.role ?? 'Viewer';
  const manager = ['Owner', 'Admin'].includes(role);
  const reports = useQuery({ ...reportsQuery(projectId), enabled: manager });
  const items = reports.data ?? [];
  const [actionError, setError] = useState('');
  const error =
    actionError || roleResult.error?.message || reports.error?.message || '';
  const [busy, setBusy] = useState(false);
  const [link, setLink] = useState('');
  const [copied, setCopied] = useState(false);
  const confirm = useConfirm();
  const setItems = (update: (current: SharedReport[]) => SharedReport[]) =>
    queryClient.setQueryData<SharedReport[]>(
      reportsQuery(projectId).queryKey,
      (current = []) => update(current),
    );
  if (!['Owner', 'Admin'].includes(role)) return null;
  async function create() {
    if (busy || !(await confirm(t('confirmShare')))) return;
    setBusy(true);
    setError('');
    try {
      const saved = await api<{ id: number; path: string; expiresAt: string }>(
        `/projects/${projectId}/reports`,
        { method: 'POST', body: '{}' },
      );
      setLink(new URL(saved.path, location.origin).href);
      setCopied(false);
      setItems((current) => [
        {
          id: saved.id,
          createdAt: new Date().toISOString(),
          expiresAt: saved.expiresAt,
        },
        ...current,
      ]);
    } catch (cause) {
      setError((cause as Error).message);
    } finally {
      setBusy(false);
    }
  }
  async function revoke(id: number) {
    if (!(await confirm(t('confirmRevoke')))) return;
    try {
      await api(`/projects/${projectId}/reports/${id}`, { method: 'DELETE' });
      setItems((current) => current.filter((item) => item.id !== id));
      setLink('');
    } catch (cause) {
      setError((cause as Error).message);
    }
  }
  return (
    <section className="analysis-card share-report-card">
      <h2>{t('shareableReport')}</h2>
      <p>{t('shareableDetail')}</p>
      <Notice error={error} />
      <button className="secondary-action" disabled={busy} onClick={create}>
        {t('createReportLink')}
      </button>
      {link && (
        <div>
          <label>
            {t('reportLink')}
            <input
              readOnly
              value={link}
              onFocus={(event) => event.currentTarget.select()}
            />
          </label>
          <button
            onClick={async () => {
              try {
                await navigator.clipboard.writeText(link);
                setCopied(true);
              } catch {
                setError(t('copyManually'));
              }
            }}
          >
            {t(copied ? 'copied' : 'copyLink')}
          </button>
          <a href={link} target="_blank" rel="noreferrer">
            {t('preview')}
          </a>
        </div>
      )}
      <ul>
        {items.map((item) => (
          <li key={item.id}>
            <span>
              {t('expires')} {format.dateTime(item.expiresAt)}
            </span>
            <button className="danger-action" onClick={() => revoke(item.id)}>
              {t('revoke')}
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}

export function PublicReportView({ token }: { token: string }) {
  // The report has its own language switch; it is not remembered.
  const [language, setLanguage] = useState<Language>(storedLanguage);
  return (
    <LanguageScope language={language}>
      <PublicReport
        token={token}
        onToggleLanguage={() =>
          setLanguage((value) => (value === 'EN' ? 'SV' : 'EN'))
        }
      />
    </LanguageScope>
  );
}

function PublicReport({
  token,
  onToggleLanguage,
}: {
  token: string;
  onToggleLanguage: () => void;
}) {
  const t = useT(insightsText);
  const { format } = useLanguage();
  const report = useQuery(publicReportQuery(token));
  const data = report.data;
  const error = report.error?.message ?? '';
  return (
    <main className="public-report page" data-theme="light">
      <header className="page-heading">
        <div>
          <p className="eyebrow">SPRINTFLOW · {t('sharedReport')}</p>
          <h1>{data ? String(data.projectName) : t('projectReport')}</h1>
        </div>
        <div className="card-actions">
          <button onClick={onToggleLanguage}>{t('otherLanguage')}</button>
          <button onClick={() => window.print()}>{t('print')}</button>
        </div>
      </header>
      <Notice error={error} />
      {!data && !error && <p role="status">{t('loadingReport')}</p>}
      {data && (
        <>
          <p>{String(data.description ?? '')}</p>
          <p>
            {t('generated')} {format.dateTime(String(data.generatedAt))}
          </p>
          {!!data.sprintName && <h2>{String(data.sprintName)}</h2>}
          <div className="metric-grid">
            {(
              [
                ['taskCount', t('tasks')],
                ['completionRate', t('completedPercent')],
                ['velocityStoryPoints', t('deliveredPoints')],
                ['cycleTimeHours', t('cycleTimeHours')],
              ] as const
            ).map(([key, label]) => (
              <article className="analysis-card" key={key}>
                <h2>{label}</h2>
                <strong>{data[key] == null ? '—' : String(data[key])}</strong>
              </article>
            ))}
          </div>
          <section className="analysis-card">
            <h2>{t('workflow')}</h2>
            {Object.entries(
              (data.statusCounts ?? {}) as Record<string, number>,
            ).map(([status, count]) => (
              <p key={status}>
                {flowStatus(t, status)}: {count}
              </p>
            ))}
          </section>
          <p>{t('snapshotNote')}</p>
        </>
      )}
    </main>
  );
}
