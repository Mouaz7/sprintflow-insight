import type { Language } from '../../api/contracts';

/**
 * A small ICU-style formatter. Plural forms follow the CLDR categories from
 * Intl.PluralRules (`#` is replaced by the localized count); dates and numbers
 * use Intl with the locale of the active language.
 */
export type PluralForms = Partial<
  Record<Exclude<Intl.LDMLPluralRule, 'other'>, string>
> & { other: string };

export type Formatter = {
  readonly language: Language;
  readonly locale: string;
  plural(count: number, forms: PluralForms): string;
  date(
    value: Date | string | number,
    options?: Intl.DateTimeFormatOptions,
  ): string;
  dateTime(
    value: Date | string | number,
    options?: Intl.DateTimeFormatOptions,
  ): string;
  number(value: number, options?: Intl.NumberFormatOptions): string;
};

export const locales: Record<Language, string> = {
  EN: 'en-GB',
  SV: 'sv-SE',
};

const formatters = new Map<Language, Formatter>();

export function formatter(language: Language): Formatter {
  const cached = formatters.get(language);
  if (cached) return cached;
  const locale = locales[language];
  const rules = new Intl.PluralRules(locale);
  const numbers = new Intl.NumberFormat(locale);
  const created: Formatter = {
    language,
    locale,
    plural(count, forms) {
      const exact = count === 0 ? forms.zero : undefined;
      const template = exact ?? forms[rules.select(count)] ?? forms.other;
      return template.replaceAll('#', numbers.format(count));
    },
    date(value, options) {
      return new Date(value).toLocaleDateString(locale, options);
    },
    dateTime(value, options) {
      return new Date(value).toLocaleString(locale, options);
    },
    number(value, options) {
      return options
        ? value.toLocaleString(locale, options)
        : numbers.format(value);
    },
  };
  formatters.set(language, created);
  return created;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type MessageFunction = (args: any, format: Formatter) => string;
export type Message = string | MessageFunction;
export type Messages = Readonly<Record<string, Message>>;

/** The Swedish entries must cover every English key with the same arguments. */
export type Translation<M extends Messages> = {
  readonly [K in keyof M]: M[K] extends string
    ? string | ((args: undefined, format: Formatter) => string)
    : M[K];
};

export type Lexicon<M extends Messages> = {
  readonly EN: M;
  readonly SV: Translation<M>;
};

/** Declares a typed lexicon; a key missing from either language is a compile error. */
export function defineLexicon<const M extends Messages>(lexicon: {
  EN: M;
  SV: Translation<M>;
}): Lexicon<M> {
  return lexicon;
}

type Arguments<E> = E extends (args: infer A, format: Formatter) => string
  ? undefined extends A
    ? [args?: A]
    : [args: A]
  : [];

export type Translate<M extends Messages> = <K extends keyof M & string>(
  key: K,
  ...args: Arguments<M[K]>
) => string;

export function translator<M extends Messages>(
  lexicon: Lexicon<M>,
  language: Language,
): Translate<M> {
  const entries = lexicon[language] as Messages;
  const format = formatter(language);
  return ((key: string, args?: unknown) => {
    const entry = entries[key] ?? lexicon.EN[key];
    if (entry === undefined) return key;
    return typeof entry === 'string' ? entry : entry(args, format);
  }) as Translate<M>;
}
