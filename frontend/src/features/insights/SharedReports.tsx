import { useEffect, useState } from 'react';
import { api } from '../../api/client';
import type { Language } from '../../api/contracts';
import { Notice, useConfirm } from '../../shared/ui/ui';

export function ShareReportCard({
  projectId,
  language,
}: {
  projectId: number;
  language: Language;
}) {
  const sv = language === 'SV';
  const [items, setItems] = useState<
    { id: number; createdAt: string; expiresAt: string }[]
  >([]);
  const [role, setRole] = useState('Viewer');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [link, setLink] = useState('');
  const [copied, setCopied] = useState(false);
  const confirm = useConfirm();
  useEffect(() => {
    let live = true;
    setRole('Viewer');
    setItems([]);
    setLink('');
    api<{ role: string }>(`/projects/${projectId}/role`)
      .then(async (value) => {
        if (live) setRole(value.role);
        if (['Owner', 'Admin'].includes(value.role)) {
          const list = await api<typeof items>(
            `/projects/${projectId}/reports`,
          );
          if (live) setItems(list);
        }
      })
      .catch((cause) => {
        if (live) setError(cause.message);
      });
    return () => {
      live = false;
    };
  }, [projectId]);
  if (!['Owner', 'Admin'].includes(role)) return null;
  async function create() {
    if (
      busy ||
      !(await confirm(
        sv
          ? 'Skapa en delbar rapport? Alla med länken kan läsa projektbeskrivningen och sammanställda mätvärden i sju dagar.'
          : 'Create a shared report? Anyone with the link can read the project description and aggregate metrics for seven days.',
      ))
    )
      return;
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
    if (
      !(await confirm(
        sv ? 'Återkalla rapportlänken?' : 'Revoke this report link?',
      ))
    )
      return;
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
      <h2>{sv ? 'Delbar projektrapport' : 'Shareable project report'}</h2>
      <p>
        {sv
          ? 'En skrivskyddad ögonblicksbild utan medlemslistor, kommentarer eller inloggningsuppgifter.'
          : 'A read-only snapshot without member lists, comments or account credentials.'}
      </p>
      <Notice language={language} error={error} />
      <button className="secondary-action" disabled={busy} onClick={create}>
        {sv ? 'Skapa rapportlänk' : 'Create report link'}
      </button>
      {link && (
        <div>
          <label>
            {sv ? 'Rapportlänk' : 'Report link'}
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
                setError(
                  sv
                    ? 'Kopiera länken manuellt från fältet.'
                    : 'Copy the link manually from the field.',
                );
              }
            }}
          >
            {copied
              ? sv
                ? 'Kopierad'
                : 'Copied'
              : sv
                ? 'Kopiera länk'
                : 'Copy link'}
          </button>
          <a href={link} target="_blank" rel="noreferrer">
            {sv ? 'Förhandsvisa' : 'Preview'}
          </a>
        </div>
      )}
      <ul>
        {items.map((item) => (
          <li key={item.id}>
            <span>
              {sv ? 'Giltig till' : 'Expires'}{' '}
              {new Date(item.expiresAt).toLocaleString(sv ? 'sv-SE' : 'en-GB')}
            </span>
            <button className="danger-action" onClick={() => revoke(item.id)}>
              {sv ? 'Återkalla' : 'Revoke'}
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}

export function PublicReportView({ token }: { token: string }) {
  const [data, setData] = useState<Record<string, unknown>>();
  const [error, setError] = useState('');
  const [sv, setSv] = useState(
    localStorage.getItem('sprintflow-language') === 'SV',
  );
  useEffect(() => {
    let live = true;
    api<Record<string, unknown>>(`/shared-reports/${encodeURIComponent(token)}`)
      .then((value) => {
        if (live) setData(value);
      })
      .catch((cause) => {
        if (live) setError(cause.message);
      });
    return () => {
      live = false;
    };
  }, [token]);
  return (
    <main className="public-report page" data-theme="light">
      <header className="page-heading">
        <div>
          <p className="eyebrow">
            SPRINTFLOW · {sv ? 'DELAD RAPPORT' : 'SHARED REPORT'}
          </p>
          <h1>
            {data
              ? String(data.projectName)
              : sv
                ? 'Projektrapport'
                : 'Project report'}
          </h1>
        </div>
        <div className="card-actions">
          <button onClick={() => setSv((value) => !value)}>
            {sv ? 'English' : 'Svenska'}
          </button>
          <button onClick={() => window.print()}>
            {sv ? 'Skriv ut' : 'Print'}
          </button>
        </div>
      </header>
      <Notice language={sv ? 'SV' : 'EN'} error={error} />
      {!data && !error && (
        <p role="status">{sv ? 'Laddar rapport…' : 'Loading report…'}</p>
      )}
      {data && (
        <>
          <p>{String(data.description ?? '')}</p>
          <p>
            {sv ? 'Skapad' : 'Generated'}{' '}
            {new Date(String(data.generatedAt)).toLocaleString(
              sv ? 'sv-SE' : 'en-GB',
            )}
          </p>
          {!!data.sprintName && <h2>{String(data.sprintName)}</h2>}
          <div className="metric-grid">
            {[
              ['taskCount', sv ? 'Uppgifter' : 'Tasks'],
              ['completionRate', sv ? 'Klart (%)' : 'Completed (%)'],
              [
                'velocityStoryPoints',
                sv ? 'Levererade poäng' : 'Delivered points',
              ],
              ['cycleTimeHours', sv ? 'Cykeltid (h)' : 'Cycle time (h)'],
            ].map(([key, label]) => (
              <article className="analysis-card" key={key}>
                <h2>{label}</h2>
                <strong>{data[key] == null ? '—' : String(data[key])}</strong>
              </article>
            ))}
          </div>
          <section className="analysis-card">
            <h2>{sv ? 'Arbetsflöde' : 'Workflow'}</h2>
            {Object.entries(
              (data.statusCounts ?? {}) as Record<string, number>,
            ).map(([status, count]) => (
              <p key={status}>
                {sv
                  ? ((
                      {
                        'To Do': 'Att göra',
                        'In Progress': 'Pågående',
                        Review: 'Granskning',
                        Done: 'Klart',
                      } as Record<string, string>
                    )[status] ?? status)
                  : status}
                : {count}
              </p>
            ))}
          </section>
          <p>
            {sv
              ? 'Det här är en ögonblicksbild. Rapportlänken kan återkallas av projektägaren.'
              : 'This is a snapshot. The project owner can revoke this report link.'}
          </p>
        </>
      )}
    </main>
  );
}
