import { describe, expect, it } from 'vitest';
import { defineLexicon, formatter, translator } from './lexicon';

const sample = defineLexicon({
  EN: {
    title: 'Tasks',
    count: ({ count }: { count: number }, f) =>
      f.plural(count, { zero: 'No tasks', one: '# task', other: '# tasks' }),
    due: ({ date }: { date: string }, f) =>
      `Due ${f.date(date, { day: 'numeric', month: 'long' })}`,
  },
  SV: {
    title: 'Uppgifter',
    count: ({ count }, f) =>
      f.plural(count, {
        zero: 'Inga uppgifter',
        one: '# uppgift',
        other: '# uppgifter',
      }),
    due: ({ date }, f) =>
      `Förfaller ${f.date(date, { day: 'numeric', month: 'long' })}`,
  },
});

describe('Typed lexicon', () => {
  it('translates plain keys in both languages', () => {
    expect(translator(sample, 'EN')('title')).toBe('Tasks');
    expect(translator(sample, 'SV')('title')).toBe('Uppgifter');
  });

  it('selects CLDR plural forms and formats the count per locale', () => {
    const en = translator(sample, 'EN');
    const sv = translator(sample, 'SV');
    expect(en('count', { count: 0 })).toBe('No tasks');
    expect(en('count', { count: 1 })).toBe('1 task');
    expect(en('count', { count: 1200 })).toBe('1,200 tasks');
    expect(sv('count', { count: 1 })).toBe('1 uppgift');
    expect(sv('count', { count: 3 })).toBe('3 uppgifter');
    expect(sv('count', { count: 1200 })).toMatch(/^1\s200 uppgifter$/);
  });

  it('formats dates with the locale of the language', () => {
    expect(translator(sample, 'EN')('due', { date: '2026-03-05' })).toBe(
      'Due 5 March',
    );
    expect(translator(sample, 'SV')('due', { date: '2026-03-05' })).toBe(
      'Förfaller 5 mars',
    );
    expect(formatter('SV').locale).toBe('sv-SE');
  });

  it('rejects unknown keys, missing translations and wrong arguments at compile time', () => {
    const t = translator(sample, 'EN');
    // @ts-expect-error: the key does not exist in the lexicon.
    expect(t('missing')).toBe('missing');
    // @ts-expect-error: a plural entry requires its count.
    expect(() => t('count')).toThrow();
    defineLexicon({
      EN: { a: 'A', b: 'B' },
      // @ts-expect-error: the Swedish lexicon lacks `b`.
      SV: { a: 'A' },
    });
  });
});
