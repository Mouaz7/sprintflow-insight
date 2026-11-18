import { useState } from 'react';
import { Link } from 'react-router';
import type {
  FlowDistribution,
  FlowReport,
  FlowSignal,
} from '../../api/contracts';
import { useT } from '../../shared/i18n/LanguageProvider';
import { CumulativeFlowChart, LineChart } from '../../shared/ui/charts/Chart';
import { Histogram } from '../../shared/ui/charts/Histogram';
import { flowText } from './i18n';

export function FlowCharts({ data }: { data: FlowReport }) {
  const t = useT(flowText);
  return (
    <>
      <section className="flow-panel">
        <h2>{t('cfd')}</h2>
        <p className="flow-help">{t('cfdHint')}</p>
        <CumulativeFlowChart
          title={t('cfd')}
          data={data.cumulativeFlow.map((day) => ({
            label: day.date,
            values: [day.done, day.review, day.inProgress, day.toDo],
          }))}
          series={[t('done'), t('review'), t('inProgress'), t('toDo')]}
        />
      </section>
      <div className="flow-chart-grid">
        <Distribution
          title={t('cycle')}
          hint={t('cycleHint')}
          values={data.cycleTimeHours}
          samples={data.completions
            .map((row) => row.cycleTimeHours)
            .filter((value): value is number => value !== null)}
        />
        <Distribution
          title={t('lead')}
          hint={t('leadHint')}
          values={data.leadTimeHours}
          samples={data.completions
            .map((row) => row.leadTimeHours)
            .filter((value): value is number => value !== null)}
        />
      </div>
      <ControlCharts data={data} />
    </>
  );
}

function Distribution({
  title,
  hint,
  values,
  samples,
}: {
  title: string;
  hint: string;
  values: FlowDistribution;
  samples: number[];
}) {
  const t = useT(flowText);
  return (
    <section className="flow-panel">
      <h2>{title}</h2>
      <p className="flow-help">{hint}</p>
      <p>{t('samples', { count: values.count })}</p>
      <dl className="flow-statistics">
        {(['mean', 'p50', 'p85', 'p95'] as const).map((key) => (
          <div key={key}>
            <dt>
              {t(key)} ({t('hours')})
            </dt>
            <dd>
              {values[key] === null ? '—' : t('number', { value: values[key] })}
            </dd>
          </div>
        ))}
      </dl>
      <Histogram
        title={`${title} (${t('hours')})`}
        samples={samples}
        bins={8}
      />
      <p className="flow-help">{t('distributionHint')}</p>
    </section>
  );
}

const signals = {
  OUTSIDE_LIMITS: 'signalLimit',
  TWO_OF_THREE_BEYOND_TWO_SIGMA: 'signalTwo',
  FOUR_OF_FIVE_BEYOND_ONE_SIGMA: 'signalFour',
  RUN_OF_EIGHT: 'signalRun',
  LARGE_MOVING_RANGE: 'signalRange',
} as const satisfies Record<FlowSignal, keyof typeof flowText.EN>;

function ControlCharts({ data }: { data: FlowReport }) {
  const t = useT(flowText);
  const [limit, setLimit] = useState(50);
  const chart = data.controlChart;
  const points = chart.points.slice(-200);
  const flagged = chart.points
    .filter((point) => point.signals.length)
    .reverse();
  return (
    <section className="flow-panel">
      <h2>{t('xmr')}</h2>
      <p className="flow-help">{t('xmrHint')}</p>
      {!chart.available ? (
        <p>{t('tooFew', { count: chart.minimumPoints })}</p>
      ) : (
        <>
          {chart.points.length > 200 && (
            <p className="flow-help">{t('chartWindow')}</p>
          )}
          <LineChart
            title={t('individuals')}
            series={[t('observations'), t('centre'), t('upper'), t('lower')]}
            data={points.map((point) => ({
              label: point.key,
              values: [
                point.cycleTimeHours,
                chart.mean ?? 0,
                chart.upperLimit ?? 0,
                chart.lowerLimit ?? 0,
              ],
            }))}
          />
          <LineChart
            title={t('ranges')}
            series={[t('ranges'), t('centre'), t('upper')]}
            data={points
              .filter((point) => point.movingRange !== null)
              .map((point) => ({
                label: point.key,
                values: [
                  point.movingRange ?? 0,
                  chart.movingRangeMean ?? 0,
                  chart.upperRangeLimit ?? 0,
                ],
              }))}
          />
          <h3>
            {t('signals')} ({chart.signalCount})
          </h3>
          {!flagged.length && <p>{t('noSignals')}</p>}
          <ul className="flow-work-list">
            {flagged.slice(0, limit).map((point) => (
              <li key={point.taskId}>
                <Link to={`/tasks?task=${encodeURIComponent(point.key)}`}>
                  {point.key}
                </Link>
                <span>
                  {t('date', { value: point.completedAt })} ·{' '}
                  {point.signals.map((signal) => t(signals[signal])).join('; ')}
                </span>
              </li>
            ))}
          </ul>
          {flagged.length > limit && (
            <button
              className="secondary-action"
              onClick={() => setLimit(limit + 50)}
            >
              {t('more')}
            </button>
          )}
        </>
      )}
    </section>
  );
}
