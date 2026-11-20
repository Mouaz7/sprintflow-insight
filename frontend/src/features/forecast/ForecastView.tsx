import { useState, type FormEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router';
import type { Project } from '../../api/contracts';
import { ApiError } from '../../api/client';
import { epicsQuery } from '../../api/queries';
import { useT } from '../../shared/i18n/LanguageProvider';
import { EmptyState, ErrorState, Skeleton } from '../../shared/ui/States';
import { PageHeader } from '../../shared/ui/ui';
import { DateInput } from '../../shared/ui/DateInput';
import { FormGuide } from '../../shared/ui/Guidance';
import { ForecastResults } from './ForecastResults';
import { ForecastCalibration } from './ForecastCalibration';
import { forecastQuery } from './queries';
import { forecastText } from './i18n';

export function ForecastView({ project }: { project: Project | undefined }) {
  const t = useT(forecastText);
  const [params, setParams] = useSearchParams();
  return (
    <section className="page forecast-page">
      <PageHeader
        guide="forecast"
        eyebrow={t('eyebrow')}
        title={t('title')}
        description={t('description')}
      />
      {project ? (
        <ProjectForecast
          key={`${project.id}:${params.toString()}`}
          projectId={project.id}
          params={params}
          apply={setParams}
        />
      ) : (
        <EmptyState title={t('noProject')} />
      )}
    </section>
  );
}

function ProjectForecast({
  projectId,
  params,
  apply,
}: {
  projectId: number;
  params: URLSearchParams;
  apply: (params: URLSearchParams) => void;
}) {
  const t = useT(forecastText);
  const [basis, setBasis] = useState(
    params.get('basis') === 'VELOCITY' ? 'VELOCITY' : 'THROUGHPUT',
  );
  const [epic, setEpic] = useState(params.get('epicId') ?? '');
  const [remaining, setRemaining] = useState(params.get('remaining') ?? '');
  const [date, setDate] = useState(params.get('targetDate') ?? '');
  const [target, setTarget] = useState(params.get('targetItems') ?? '');
  const args = new URLSearchParams();
  for (const name of [
    'basis',
    'epicId',
    'remaining',
    'targetDate',
    'targetItems',
  ]) {
    const value = params.get(name);
    if (value) args.set(name, value);
  }
  const query = useQuery(forecastQuery(projectId, args.toString()));
  const epics = useQuery(epicsQuery(projectId));
  const today = query.data?.start ?? new Date().toISOString().slice(0, 10);
  const maxDate = new Date(Date.parse(`${today}T00:00:00Z`) + 1825 * 86400000)
    .toISOString()
    .slice(0, 10);
  function submit(event: FormEvent) {
    event.preventDefault();
    const next = new URLSearchParams({ basis });
    if (epic) next.set('epicId', epic);
    if (remaining !== '') next.set('remaining', String(Number(remaining)));
    if (date) next.set('targetDate', date);
    if (date && target !== '') next.set('targetItems', String(Number(target)));
    apply(next);
  }
  return (
    <>
      <form className="forecast-panel forecast-form" onSubmit={submit}>
        <FormGuide topic="forecast" />
        <div className="forecast-fields">
          <label>
            {t('basis')}
            <select
              value={basis}
              onChange={(event) => {
                setBasis(event.target.value);
                setRemaining('');
                setTarget('');
              }}
            >
              <option value="THROUGHPUT">{t('throughput')}</option>
              <option value="VELOCITY">{t('velocity')}</option>
            </select>
          </label>
          <label>
            {t('epic')}
            <select
              value={epic}
              onChange={(event) => {
                setEpic(event.target.value);
                setRemaining('');
                setTarget('');
              }}
            >
              <option value="">{t('all')}</option>
              {epic &&
                epics.data &&
                !epics.data.some((item) => String(item.id) === epic) && (
                  <option value={epic} disabled>
                    {t('unavailableEpic')}
                  </option>
                )}
              {epics.data?.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.title}
                </option>
              ))}
            </select>
          </label>
          <label>
            {t('remaining')}
            <input
              type="number"
              min="0"
              max="10000"
              step="1"
              value={remaining}
              placeholder={t('auto')}
              onChange={(event) => setRemaining(event.target.value)}
            />
          </label>
          <label>
            {t('dateField')}
            <DateInput
              ariaLabel={t('dateField')}
              value={date}
              min={today}
              max={maxDate}
              onChange={(value) => {
                setDate(value);
                if (!value) setTarget('');
              }}
            />
          </label>
          <label>
            {t('targetField')}
            <input
              type="number"
              min="0"
              max="10000"
              step="1"
              value={target}
              placeholder={t('auto')}
              disabled={!date}
              onChange={(event) => setTarget(event.target.value)}
            />
          </label>
        </div>
        <p className="forecast-help">{t('formHint')}</p>
        <div className="forecast-actions">
          <button className="primary-action">{t('apply')}</button>
          <button
            type="button"
            className="secondary-action"
            onClick={() => apply(new URLSearchParams())}
          >
            {t('reset')}
          </button>
        </div>
      </form>
      {query.isError || epics.isError ? (
        <ErrorState
          detail={
            query.error instanceof ApiError && query.error.status === 400
              ? t('invalidInputs')
              : t('failed')
          }
          onRetry={() => {
            void query.refetch();
            void epics.refetch();
          }}
        />
      ) : query.isPending || epics.isPending ? (
        <Skeleton label={t('loading')} />
      ) : (
        <>
          <ForecastCalibration projectId={projectId} basis={query.data.basis} />
          <ForecastResults data={query.data} />
        </>
      )}
    </>
  );
}
