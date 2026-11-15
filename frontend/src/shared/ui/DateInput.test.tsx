import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';
import { normalizePoints, validDate, validPoints } from '../lib/formValues';
import { DateInput } from './DateInput';

function DateEditor() {
  const [date, setDate] = useState('');
  return (
    <label>
      Start date
      <DateInput value={date} onChange={setDate} language="EN" />
    </label>
  );
}

describe('Date and points input', () => {
  it('keeps typed digits in order and supports clearing and editing a date', async () => {
    const user = userEvent.setup();
    render(<DateEditor />);
    const input = screen.getByLabelText('Start date');
    await user.type(input, '20240706');
    expect(input).toHaveValue('2024-07-06');
    await user.clear(input);
    await user.type(input, '2026-07-06');
    expect(input).toHaveValue('2026-07-06');
    await user.clear(input);
    await user.type(input, '2026-02-20');
    await user.type(input, '{Backspace}5');
    expect(input).toHaveValue('2026-02-25');
  });

  it.each([
    '0001-01-01',
    '1900-01-01',
    '2024-02-29',
    '2424-02-05',
    '9999-12-31',
  ])(
    'accepts an actual four-digit date without a business-year restriction: %s',
    (date) => {
      expect(validDate(date)).toBe(true);
    },
  );

  it.each([
    '2023-02-29',
    '2024-02-31',
    '202407-06-08',
    '0000-01-01',
    '2024-13-01',
  ])('rejects malformed calendar values: %s', (date) =>
    expect(validDate(date)).toBe(false),
  );

  it('removes leading zeroes without forcing an empty points field back to zero', () => {
    expect(normalizePoints('05')).toBe('5');
    expect(normalizePoints('00050')).toBe('50');
    expect(normalizePoints('0')).toBe('0');
    expect(normalizePoints('')).toBe('');
    expect(validPoints('1000', 1000)).toBe(true);
    for (const value of ['', '-1', '1.5', '1e2', '1001'])
      expect(validPoints(value, 1000)).toBe(false);
  });
});
