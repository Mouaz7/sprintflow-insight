import { useT } from '../../i18n/LanguageProvider';
import { BarChart } from './Chart';
import { histogram } from './chartMath';
import { chartText } from './i18n';
export function Histogram({
  title,
  samples,
  bins = 6,
}: {
  title: string;
  samples: readonly number[];
  bins?: number;
}) {
  const t = useT(chartText);
  return (
    <BarChart
      title={title}
      data={histogram(samples, bins)}
      series={[t('count')]}
    />
  );
}
