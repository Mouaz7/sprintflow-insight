export const MIN_DATE = '0001-01-01';
export const MAX_DATE = '9999-12-31';
const DATE_PATTERN = /^(?!0000)\d{4}-\d{2}-\d{2}$/;

export function normalizeDate(value: string): string {
  return /^\d{8}$/.test(value)
    ? `${value.slice(0, 4)}-${value.slice(4, 6)}-${value.slice(6)}`
    : value;
}
export const DATE_ERROR = 'Enter a valid date with a four-digit year.';

export function validDate(value: string): boolean {
  if (!DATE_PATTERN.test(value) || value < MIN_DATE || value > MAX_DATE)
    return false;
  const date = new Date(`${value}T00:00:00Z`);
  return (
    !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value
  );
}

export function normalizePoints(value: string): string {
  return value.replace(/^0+(?=\d)/, '');
}

export function validPoints(value: string, maximum: number): boolean {
  return (
    /^\d+$/.test(value) &&
    Number.isSafeInteger(Number(value)) &&
    Number(value) <= maximum
  );
}
