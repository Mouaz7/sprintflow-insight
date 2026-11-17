/* eslint-disable jsx-a11y/no-noninteractive-tabindex -- The timeline overflow region needs keyboard scrolling. */
import { useId } from 'react';
import type { RoadmapSnapshot } from '../../api/contracts';
import { useT } from '../../shared/i18n/LanguageProvider';
import { roadmapText } from './i18n';

const day = (value: string) => Date.parse(`${value}T00:00:00Z`) / 86400000;
export function Timeline({ data }: { data: RoadmapSnapshot }) {
  const t = useT(roadmapText);
  const id = useId();
  const rows = [
    ...data.epics.map((row) => ({
      key: `e${row.epic.id}`,
      title: row.epic.title,
      start: row.plannedStart,
      end: row.plannedEnd,
      progress: row.progress,
      color: row.epic.color,
      kind: t('epic'),
    })),
    ...data.sprints.map((row) => ({
      key: `s${row.id}`,
      title: row.name,
      start: row.startDate,
      end: row.endDate,
      progress: null,
      color: 'var(--chart-1)',
      kind: t('sprint'),
    })),
  ];
  const dates = rows
    .flatMap((row) => [row.start, row.end])
    .filter((value): value is string => !!value)
    .map(day);
  const first = Math.min(day(data.today), ...dates),
    last = Math.max(first + 7, ...dates);
  const x = (date: string) =>
    260 + ((day(date) - first) / (last - first)) * 650;
  const y = (index: number) => 70 + index * 52;
  const position = new Map(rows.map((row, index) => [row.key, { row, index }]));
  return (
    <section className="roadmap-panel">
      <h2 id={`${id}-title`}>{t('timeline')}</h2>
      <p id={`${id}-help`} className="roadmap-help">
        {t('timelineHint')}
      </p>
      {/* The overflow region needs focus so arrow keys can scroll the timeline. */}
      {!!rows.length && (
        <div
          className="roadmap-scroll"
          role="region"
          tabIndex={0}
          aria-labelledby={`${id}-title`}
          aria-describedby={`${id}-help`}
        >
          <svg
            className="roadmap-timeline"
            viewBox={`0 0 960 ${Math.max(150, rows.length * 52 + 95)}`}
            role="img"
            aria-labelledby={`${id}-title`}
          >
            <defs>
              <marker
                id={`${id}-arrow`}
                viewBox="0 0 10 10"
                refX="9"
                refY="5"
                markerWidth="7"
                markerHeight="7"
                orient="auto-start-reverse"
              >
                <path d="M 0 0 L 10 5 L 0 10 z" fill="var(--text)" />
              </marker>
            </defs>
            {[0, 0.25, 0.5, 0.75, 1].map((ratio) => {
              const date = new Date((first + ratio * (last - first)) * 86400000)
                .toISOString()
                .slice(0, 10);
              return (
                <g key={ratio}>
                  <text
                    x={260 + ratio * 650}
                    y="24"
                    textAnchor={
                      ratio === 1 ? 'end' : ratio === 0 ? 'start' : 'middle'
                    }
                  >
                    {t('date', { value: date })}
                  </text>
                  <line
                    className="roadmap-grid"
                    x1={260 + ratio * 650}
                    x2={260 + ratio * 650}
                    y1="38"
                    y2={rows.length * 52 + 90}
                  />
                </g>
              );
            })}
            {rows.map((row, index) => {
              const start = row.start ?? row.end,
                end = row.end ?? row.start;
              const left = start ? x(start) : 260,
                width = start && end ? Math.max(8, x(end) - left) : 0;
              return (
                <g key={row.key}>
                  <text x="8" y={y(index) - 4}>
                    <title>{row.title}</title>
                    {row.title.length > 29
                      ? `${row.title.slice(0, 28)}…`
                      : row.title}
                  </text>
                  <text className="roadmap-axis-kind" x="8" y={y(index) + 14}>
                    {row.kind}
                  </text>
                  {width ? (
                    <>
                      <rect
                        x={left}
                        y={y(index) - 12}
                        width={width}
                        height="24"
                        rx="4"
                        fill={row.color}
                        opacity="0.28"
                      />
                      <rect
                        x={left}
                        y={y(index) - 12}
                        width={Math.max(0, width * (row.progress ?? 1))}
                        height="24"
                        rx="4"
                        fill={row.color}
                      />
                      <rect
                        x={left}
                        y={y(index) - 12}
                        width={width}
                        height="24"
                        rx="4"
                        fill="none"
                        stroke="var(--text)"
                      />
                    </>
                  ) : (
                    <text x="270" y={y(index) + 4}>
                      {t('noDates')}
                    </text>
                  )}
                </g>
              );
            })}
            {data.epicLinks.slice(0, 100).map((link) => {
              const from = position.get(`e${link.fromEpicId}`),
                to = position.get(`e${link.toEpicId}`);
              if (!from || !to) return null;
              const fromDate = from.row.end ?? from.row.start,
                toDate = to.row.start ?? to.row.end;
              if (!fromDate || !toDate) return null;
              const a = x(fromDate),
                b = x(toDate),
                ay = y(from.index),
                by = y(to.index);
              return (
                <path
                  key={`${link.fromEpicId}-${link.toEpicId}`}
                  d={`M ${a} ${ay} C ${Math.min(940, a + 26)} ${ay}, ${Math.max(240, b - 26)} ${by}, ${b} ${by}`}
                  stroke="var(--text)"
                  strokeDasharray={link.open ? undefined : '4 3'}
                  strokeWidth="1.5"
                  fill="none"
                  markerEnd={`url(#${id}-arrow)`}
                >
                  <title>
                    {from.row.title} → {to.row.title}: {link.dependencies}
                  </title>
                </path>
              );
            })}
          </svg>
        </div>
      )}
      <details>
        <summary>{t('timelineTable')}</summary>
        <div
          className="roadmap-scroll"
          role="region"
          tabIndex={0}
          aria-label={t('timelineTable')}
        >
          <table>
            <caption>{t('timeline')}</caption>
            <thead>
              <tr>
                <th scope="col">
                  {t('epic')} / {t('sprint')}
                </th>
                <th scope="col">{t('startDate')}</th>
                <th scope="col">{t('targetDate')}</th>
                <th scope="col">{t('progress')}</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.key}>
                  <th scope="row">
                    {row.title} ({row.kind})
                  </th>
                  <td>
                    {row.start ? t('date', { value: row.start }) : t('noDates')}
                  </td>
                  <td>
                    {row.end ? t('date', { value: row.end }) : t('noDates')}
                  </td>
                  <td>
                    {row.progress === null
                      ? '—'
                      : t('percent', { value: row.progress })}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </section>
  );
}
