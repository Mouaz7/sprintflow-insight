export type ChartRow = { label: string; values: readonly number[]; x?: number };
export type ChartGeometry = {
  left: number;
  right: number;
  top: number;
  bottom: number;
  minimum: number;
  maximum: number;
  x: (index: number) => number;
  y: (value: number) => number;
};

export function geometry(
  data: readonly ChartRow[],
  stacked: boolean,
): ChartGeometry {
  const values = data.flatMap((row) =>
    stacked ? [row.values.reduce((sum, n) => sum + n, 0)] : [...row.values],
  );
  if (
    values.some((n) => !Number.isFinite(n)) ||
    data.some((row) => row.x !== undefined && !Number.isFinite(row.x))
  )
    throw new Error('Chart values must be finite.');
  const minimum = Math.min(0, ...values);
  const maximum = Math.max(1, ...values);
  const left = 56,
    right = 704,
    top = 20,
    bottom = 194;
  return {
    left,
    right,
    top,
    bottom,
    minimum,
    maximum,
    x: (index) =>
      left + (index * (right - left)) / Math.max(1, data.length - 1),
    y: (value) =>
      bottom - ((value - minimum) / (maximum - minimum)) * (bottom - top),
  };
}

export function histogram(samples: readonly number[], bins = 6): ChartRow[] {
  const valid = samples.filter(Number.isFinite);
  if (!valid.length) return [];
  const min = Math.min(...valid),
    max = Math.max(...valid);
  const count =
    min === max
      ? 1
      : Math.min(40, Math.max(1, Math.floor(Number.isFinite(bins) ? bins : 6)));
  const width = (max - min || 1) / count;
  const rows = Array.from({ length: count }, (_, i) => ({
    label: `${Number((min + i * width).toPrecision(4))}–${Number((min + (i + 1) * width).toPrecision(4))}`,
    values: [0],
  }));
  for (const n of valid) {
    const i = Math.min(count - 1, Math.floor((n - min) / width));
    const row = rows[i];
    if (row) row.values[0] = (row.values[0] ?? 0) + 1;
  }
  return rows;
}
