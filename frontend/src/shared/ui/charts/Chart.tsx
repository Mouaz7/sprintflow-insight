import { useId, useState } from 'react';
import { useLanguage, useT } from '../../i18n/LanguageProvider';
import { EmptyState } from '../States';
import { geometry, type ChartRow } from './chartMath';
import { chartText } from './i18n';

type Props = {
  title: string;
  data: readonly ChartRow[];
  series: readonly string[];
  unit?: string;
  ideal?: boolean;
};
type Kind = 'line' | 'bar' | 'scatter' | 'cumulative';
const dash = ['none', '8 4', '2 4', '10 3 2 3', '4 2'];

/** SVG plus the same complete data in HTML. A native range control provides arrow/Home/End exploration. */
export function Chart({
  title,
  data,
  series,
  unit = '',
  ideal = false,
  kind,
}: Props & { kind: Kind }) {
  const t = useT(chartText);
  const { format } = useLanguage();
  const id = useId();
  const [selected, setSelected] = useState(0);
  const index = Math.min(selected, Math.max(0, data.length - 1));
  const row = data[index];
  if (!data.length)
    return <EmptyState title={title} description={t('noData')} />;
  if (data.some((r) => r.values.length !== series.length))
    throw new Error('Each chart row must match its series.');
  const g = geometry(data, kind === 'cumulative');
  const value = (n: number) => `${format.number(n)}${unit ? ` ${unit}` : ''}`;
  const description = row
    ? `${row.label}: ${series.map((name, i) => `${name} ${value(row.values[i] ?? 0)}`).join('; ')}`
    : '';
  const ticks = [g.minimum, (g.minimum + g.maximum) / 2, g.maximum];
  const points = (column: number) =>
    data.map((r, i) => `${g.x(i)},${g.y(r.values[column] ?? 0)}`).join(' ');
  const xValues = data.map((r) => r.x ?? 0),
    minX = Math.min(...xValues),
    maxX = Math.max(...xValues);
  const pointX = (r: ChartRow, i: number) =>
    r.x === undefined
      ? g.x(i)
      : g.left + ((r.x - minX) / (maxX - minX || 1)) * (g.right - g.left);
  return (
    <figure className="data-chart" aria-labelledby={`${id}-title`}>
      <figcaption id={`${id}-title`}>{title}</figcaption>
      <svg
        className="data-chart-svg"
        viewBox="0 0 720 245"
        role="img"
        aria-labelledby={`${id}-title`}
        aria-describedby={`${id}-help`}
      >
        {ticks.map((n) => (
          <g key={n}>
            <line
              className="chart-grid"
              x1={g.left}
              x2={g.right}
              y1={g.y(n)}
              y2={g.y(n)}
            />
            <text
              className="chart-axis"
              x={g.left - 10}
              y={g.y(n) + 4}
              textAnchor="end"
            >
              {format.number(n)}
            </text>
          </g>
        ))}
        {ideal && (
          <line
            className="chart-ideal"
            x1={g.left}
            x2={g.right}
            y1={g.top}
            y2={g.bottom}
          />
        )}
        {series.map((name, column) => {
          const color = `var(--chart-${(column % 5) + 1})`;
          if (kind === 'cumulative') {
            const high = data.map(
              (r, i) =>
                `${g.x(i)},${g.y(r.values.slice(0, column + 1).reduce((sum, n) => sum + n, 0))}`,
            );
            const low = data
              .map(
                (r, i) =>
                  `${g.x(i)},${g.y(r.values.slice(0, column).reduce((sum, n) => sum + n, 0))}`,
              )
              .reverse();
            return (
              <polygon
                key={name}
                className="chart-area"
                fill={color}
                stroke={color}
                points={[...high, ...low].join(' ')}
              />
            );
          }
          if (kind === 'line')
            return (
              <polyline
                key={name}
                className="chart-line"
                stroke={color}
                strokeDasharray={dash[column % 5]}
                points={points(column)}
              />
            );
          return data.map((r, i) => {
            const n = r.values[column] ?? 0;
            if (kind === 'bar') {
              const group = (g.right - g.left) / Math.max(1, data.length),
                width = Math.max(1, group / (series.length + 1));
              return (
                <rect
                  key={`${name}-${i}`}
                  fill={color}
                  x={g.left + i * group + column * width + 3}
                  y={Math.min(g.y(n), g.y(0))}
                  width={width}
                  height={Math.abs(g.y(0) - g.y(n))}
                />
              );
            }
            return (
              <circle
                key={`${name}-${i}`}
                fill={color}
                cx={pointX(r, i)}
                cy={g.y(n)}
                r={index === i ? 6 : 4}
              >
                <title>
                  {r.label}: {name} {value(n)}
                </title>
              </circle>
            );
          });
        })}
        {row &&
          kind === 'line' &&
          row.values.map((n, column) => (
            <circle
              key={column}
              className="chart-selected"
              cx={g.x(index)}
              cy={g.y(n)}
              r={5}
            />
          ))}
        {[0, Math.floor((data.length - 1) / 2), data.length - 1]
          .filter((n, i, a) => a.indexOf(n) === i)
          .map((i) => (
            <text
              key={i}
              className="chart-axis"
              x={
                kind === 'bar'
                  ? g.left + ((i + 0.5) * (g.right - g.left)) / data.length
                  : g.x(i)
              }
              y="226"
              textAnchor="middle"
            >
              {data[i]?.label}
            </text>
          ))}
      </svg>
      <ul className="chart-legend">
        {series.map((name, column) => (
          <li key={name}>
            <span
              className={`chart-swatch chart-color-${(column % 5) + 1}`}
              aria-hidden="true"
            >
              {column + 1}
            </span>
            {name}
          </li>
        ))}
      </ul>
      <div className="chart-explorer">
        <label htmlFor={`${id}-point`}>{t('explore')}</label>
        <input
          id={`${id}-point`}
          type="range"
          min={0}
          max={Math.max(0, data.length - 1)}
          value={index}
          onChange={(event) => setSelected(Number(event.target.value))}
          aria-valuetext={description}
          aria-describedby={`${id}-help`}
        />
        <output htmlFor={`${id}-point`} aria-live="polite">
          {description}
        </output>
        <p id={`${id}-help`}>{t('keyboard')}</p>
      </div>
      <details className="chart-table">
        <summary>{t('table')}</summary>
        <div className="chart-table-scroll">
          <table>
            <caption>{title}</caption>
            <thead>
              <tr>
                <th scope="col">{t('position')}</th>
                {series.map((name) => (
                  <th key={name} scope="col">
                    {name}
                    {unit ? ` (${unit})` : ''}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {data.map((r, i) => (
                <tr key={`${r.label}-${i}`}>
                  <th scope="row">{r.label}</th>
                  {r.values.map((n, j) => (
                    <td key={j}>{format.number(n)}</td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </figure>
  );
}
export function LineChart(props: Props) {
  return <Chart {...props} kind="line" />;
}
export function BarChart(props: Props) {
  return <Chart {...props} kind="bar" />;
}
export function ScatterChart(props: Props) {
  return <Chart {...props} kind="scatter" />;
}
export function CumulativeFlowChart(props: Props) {
  return <Chart {...props} kind="cumulative" />;
}
