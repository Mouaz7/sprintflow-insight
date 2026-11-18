import { describe, expect, it } from 'vitest';
import { icu } from './icu';
import { defineLexicon, translator } from './lexicon';

const sample = defineLexicon({
  EN: { count: icu('emptyTaskCount'), due: icu('dueDate') },
  SV: { count: icu('emptyTaskCount'), due: icu('dueDate') },
});

describe('Precompiled ICU MessageFormat', () => {
  it('supports exact zero selectors, CLDR plurals and localized # numbers', () => {
    const en = translator(sample, 'EN');
    const sv = translator(sample, 'SV');
    expect(en('count', { count: 0 })).toBe('No tasks');
    expect(sv('count', { count: 0 })).toBe('Inga uppgifter');
    expect(en('count', { count: 1 })).toBe('1 task');
    expect(sv('count', { count: 1 })).toBe('1 uppgift');
    expect(en('count', { count: 1200 })).toBe('1,200 tasks');
    expect(sv('count', { count: 1200 })).toMatch(/^1\s200 uppgifter$/);
  });

  it('uses ICU date skeletons with the complete en-GB/sv-SE locales', () => {
    const date = new Date(2026, 2, 5, 12);
    expect(translator(sample, 'EN')('due', { date })).toBe(' · due 05/03/2026');
    expect(translator(sample, 'SV')('due', { date })).toBe(
      ' · förfaller 2026-03-05',
    );
  });

  it('retains argument checking for compiled messages', () => {
    const t = translator(sample, 'EN');
    const compileTimeAssertions = () => {
      // @ts-expect-error: compiled plural messages still require a numeric count.
      t('count', { count: 'one' });
      // @ts-expect-error: dates accept Date/epoch values, not unparsed strings.
      t('due', { date: '2026-03-05' });
      // @ts-expect-error: compiled message ids must exist.
      icu('missing');
    };
    void compileTimeAssertions;
    expect(t('count', { count: 2 })).toBe('2 tasks');
  });
});
