import { useQuery } from '@tanstack/react-query';
import { api } from '../../api/client';
import { projectScope } from '../../api/queries';
import { useLanguage } from '../../shared/i18n/LanguageProvider';
import { ErrorState, Skeleton } from '../../shared/ui/States';

type Point = {
  observed: number;
  weighted: number;
  lower: number;
  upper: number;
  signal: 'LOW' | 'IN_LIMITS' | 'HIGH';
};
type Chart = { baselineMean: number; monitoring: Point[] };
type Signals = {
  firstMonitoredDay: string | null;
  inflow: Chart | null;
  throughput: Chart | null;
};

export default function FlowSignalsPanel({ projectId }: { projectId: number }) {
  const { language, format } = useLanguage();
  const sv = language === 'SV';
  const query = useQuery({
    queryKey: [...projectScope(projectId), 'flow-signals'],
    queryFn: ({ signal }) =>
      api<Signals>(`/projects/${projectId}/flow-signals`, { signal }),
  });
  if (query.isPending) return <Skeleton />;
  if (query.isError)
    return (
      <ErrorState
        detail={
          sv
            ? 'Flödessignalerna kunde inte hämtas.'
            : 'Could not load flow signals.'
        }
        onRetry={() => void query.refetch()}
      />
    );
  const { firstMonitoredDay, inflow, throughput } = query.data;
  if (!firstMonitoredDay || !inflow || !throughput) {
    return (
      <p>
        {sv
          ? 'Minst 35 hela dagar behövs för att beräkna kontrollgränser.'
          : 'Control limits require at least 35 full days.'}
      </p>
    );
  }
  const date = (index: number) =>
    format.date(Date.parse(firstMonitoredDay) + index * 86_400_000, {
      day: 'numeric',
      month: 'short',
      timeZone: 'UTC',
    });
  const value = (n: number) => format.number(n, { maximumFractionDigits: 2 });
  const columns = sv
    ? ['Dag', 'Antal', 'Viktat värde', 'Kontrollgränser', 'Signal']
    : ['Day', 'Count', 'Weighted value', 'Control limits', 'Signal'];
  const signals = sv
    ? { LOW: 'Under gräns', IN_LIMITS: 'Inom gränserna', HIGH: 'Över gräns' }
    : { LOW: 'Below limit', IN_LIMITS: 'Within limits', HIGH: 'Above limit' };
  return (
    <>
      <p>
        {sv
          ? 'Sju hela dagar jämförs med de 28 dagarna före perioden.'
          : 'Seven full days are compared with the preceding 28 days.'}
      </p>
      {(
        [
          [sv ? 'Inflöde' : 'Inflow', inflow],
          [sv ? 'Genomströmning' : 'Throughput', throughput],
        ] as const
      ).map(([title, chart]) => (
        <div className="chart-table-scroll" key={title}>
          <table>
            <caption>{title}</caption>
            <thead>
              <tr>
                {columns.map((column) => (
                  <th key={column} scope="col">
                    {column}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {chart.monitoring.map((point, index) => (
                <tr key={index}>
                  <th scope="row">{date(index)}</th>
                  <td>{point.observed}</td>
                  <td>{value(point.weighted)}</td>
                  <td>
                    {value(point.lower)}–{value(point.upper)}
                  </td>
                  <td>{signals[point.signal]}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ))}
    </>
  );
}
