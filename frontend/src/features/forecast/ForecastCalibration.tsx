import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useT } from '../../shared/i18n/LanguageProvider';
import { defineLexicon } from '../../shared/i18n/lexicon';
import { BarChart } from '../../shared/ui/charts/Chart';
import { ErrorState, Skeleton } from '../../shared/ui/States';
import { calibrationQuery, type CalibrationReport } from './calibration';
import { forecastText } from './i18n';

const text = defineLexicon({
  EN: {
    title: 'Model comparison',
    loading: 'Comparing models against historical periods…',
    failed: 'Could not load the model comparison.',
    insufficient: ({ count, required }: { count: number; required: number }) =>
      `${count} complete periods are available. Model comparison requires ${required}.`,
    noCompletions: 'The observed periods contain no completed work.',
    'gamma-poisson': 'Bayesian Gamma–Poisson',
    'ewma-poisson': 'Exponentially weighted Poisson',
    'bootstrap-monte-carlo': 'Bootstrap Monte Carlo',
    selected: 'Selected model',
    comparison: 'Rolling-origin scores',
    method: ({
      horizons,
      training,
      origins,
    }: {
      horizons: number;
      training: number;
      origins: number;
    }) =>
      `The comparison starts with ${training} training periods and predicts the following ${horizons} periods at each of ${origins} historical starting points. The model with the lowest mean CRPS is selected. Lower CRPS and MAE are better.`,
    model: 'Model',
    coverage: 'Observed interval coverage',
    prediction: ({ periods }: { periods: number }) =>
      `Forecast for the next ${periods} periods`,
    median: 'Median',
    interval: 'Central prediction interval',
    pit: 'Predicted percentile distribution (PIT)',
    share: 'Share of backtested observations (%)',
    pitHelp:
      'A well calibrated model tends to spread observations across the percentile bins. With few or overlapping observations, the distribution can vary considerably.',
  },
  SV: {
    title: 'Modelljämförelse',
    loading: 'Jämför modeller mot historiska perioder…',
    failed: 'Modelljämförelsen kunde inte laddas.',
    insufficient: ({ count, required }: { count: number; required: number }) =>
      `Det finns ${count} hela perioder. Modelljämförelsen kräver ${required}.`,
    noCompletions:
      'Ingen av de observerade perioderna innehåller slutfört arbete.',
    'gamma-poisson': 'Bayesiansk Gamma–Poisson',
    'ewma-poisson': 'Exponentiellt viktad Poisson',
    'bootstrap-monte-carlo': 'Bootstrap Monte Carlo',
    selected: 'Vald modell',
    comparison: 'Resultat från historiska testperioder',
    method: ({
      horizons,
      training,
      origins,
    }: {
      horizons: number;
      training: number;
      origins: number;
    }) =>
      `Jämförelsen börjar med ${training} träningsperioder och prognostiserar nästa ${horizons} perioder vid var och en av ${origins} historiska startpunkter. Modellen med lägst genomsnittlig CRPS väljs. Lägre CRPS och MAE är bättre.`,
    model: 'Modell',
    coverage: 'Observerad täckning för intervallen',
    prediction: ({ periods }: { periods: number }) =>
      `Prognos för nästa ${periods} perioder`,
    median: 'Median',
    interval: 'Centralt prediktionsintervall',
    pit: 'Fördelning över prognosens percentiler (PIT)',
    share: 'Andel historiska observationer (%)',
    pitHelp:
      'En välkalibrerad modell tenderar att sprida observationerna över percentilgrupperna. Med få eller överlappande observationer kan fördelningen variera mycket.',
  },
});

export function ForecastCalibration({
  projectId,
  basis,
}: {
  projectId: number;
  basis: CalibrationReport['basis'];
}) {
  const t = useT(text);
  const [expanded, setExpanded] = useState(false);
  const query = useQuery({
    ...calibrationQuery(projectId, basis),
    enabled: expanded,
  });
  return (
    <details
      className="forecast-panel forecast-calibration"
      onToggle={(event) => setExpanded(event.currentTarget.open)}
    >
      <summary>{t('title')}</summary>
      {expanded &&
        (query.isError ? (
          <ErrorState
            detail={t('failed')}
            onRetry={() => void query.refetch()}
          />
        ) : query.isPending ? (
          <Skeleton label={t('loading')} />
        ) : (
          <CalibrationResults data={query.data} />
        ))}
    </details>
  );
}

function CalibrationResults({ data }: { data: CalibrationReport }) {
  const t = useT(text);
  const f = useT(forecastText);
  const selected = data.candidates.find(
    ({ model }) => model === data.selectedModel,
  );
  if (data.status === 'NO_COMPLETIONS') return <p>{t('noCompletions')}</p>;
  if (!selected || !data.prediction)
    return (
      <p>
        {t('insufficient', {
          count: data.availablePeriods,
          required: data.requiredPeriods,
        })}
      </p>
    );
  const unit = f(data.unit === 'ITEMS' ? 'tasks' : 'points');

  return (
    <div className="calibration-results">
      <h2>
        {t('selected')} · {t(selected.model)}
      </h2>
      <p>
        {t('method', {
          horizons: data.horizonPeriods,
          training: data.trainingPeriods,
          origins: selected.origins,
        })}
      </p>
      <h3>{t('prediction', { periods: data.horizonPeriods })}</h3>
      <dl className="forecast-stats">
        <div>
          <dt>{t('median')}</dt>
          <dd>
            {f('number', { value: data.prediction.median })} · {unit}
          </dd>
        </div>
        {data.prediction.intervals.map(({ confidence, lower, upper }) => (
          <div key={confidence}>
            <dt>
              {f('percent', { value: confidence })} · {t('interval')}
            </dt>
            <dd>
              {f('number', { value: lower })}–{f('number', { value: upper })}
            </dd>
          </div>
        ))}
      </dl>
      <div className="calibration-table">
        <table>
          <caption>
            {t('comparison')} · {unit}
          </caption>
          <thead>
            <tr>
              <th scope="col">{t('model')}</th>
              <th scope="col">CRPS</th>
              <th scope="col">MAE</th>
              <th scope="col">{t('coverage')} · 50% / 85% / 95%</th>
            </tr>
          </thead>
          <tbody>
            {data.candidates.map((candidate) => (
              <tr key={candidate.model}>
                <th scope="row">{t(candidate.model)}</th>
                <td>{f('number', { value: candidate.meanCrps })}</td>
                <td>{f('number', { value: candidate.medianAbsoluteError })}</td>
                <td>
                  {['0.5', '0.85', '0.95']
                    .map((level) => {
                      const observed = candidate.coverage[level];
                      return observed === undefined
                        ? '—'
                        : f('percent', { value: observed });
                    })
                    .join(' / ')}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p>{t('pitHelp')}</p>
      <BarChart
        title={t('pit')}
        series={[t('share')]}
        data={selected.pitHistogram.map((share, index) => ({
          label: `${index * 10}–${(index + 1) * 10}%`,
          values: [share * 100],
        }))}
      />
    </div>
  );
}
