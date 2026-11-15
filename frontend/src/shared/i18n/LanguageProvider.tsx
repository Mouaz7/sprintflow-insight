import {
  createContext,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from 'react';
import type { Language } from '../../api/contracts';
import { applyLanguage, storedLanguage } from '../lib/appearance';
import { common } from './common';
import {
  formatter,
  translator,
  type Formatter,
  type Lexicon,
  type Messages,
  type Translate,
} from './lexicon';

type LanguageState = {
  language: Language;
  setLanguage: (language: Language) => void;
};

const LanguageContext = createContext<LanguageState>({
  language: 'EN',
  setLanguage: () => {},
});

/** The application language, remembered in this browser and set on `<html lang>`. */
export function LanguageProvider({
  children,
  initial,
}: {
  children: ReactNode;
  initial?: Language | undefined;
}) {
  const [language, setLanguage] = useState<Language>(
    () => initial ?? storedLanguage(),
  );
  useEffect(() => applyLanguage(language), [language]);
  return (
    <LanguageContext value={{ language, setLanguage }}>
      {children}
    </LanguageContext>
  );
}

/** A nested language that is neither persisted nor applied to the document. */
export function LanguageScope({
  language,
  children,
}: {
  language: Language;
  children: ReactNode;
}) {
  const parent = useContext(LanguageContext);
  return (
    <LanguageContext value={{ language, setLanguage: parent.setLanguage }}>
      {children}
    </LanguageContext>
  );
}

export function useLanguage(): LanguageState & { format: Formatter } {
  const state = useContext(LanguageContext);
  return { ...state, format: formatter(state.language) };
}

const cache = new WeakMap<object, Partial<Record<Language, unknown>>>();

function cachedTranslator<M extends Messages>(
  lexicon: Lexicon<M>,
  language: Language,
): Translate<M> {
  let entry = cache.get(lexicon);
  if (!entry) cache.set(lexicon, (entry = {}));
  return (entry[language] ??= translator(lexicon, language)) as Translate<M>;
}

/** Returns a typed translate function; an unknown key does not compile. */
export function useT(): Translate<(typeof common)['EN']>;
export function useT<M extends Messages>(lexicon: Lexicon<M>): Translate<M>;
export function useT<M extends Messages>(lexicon?: Lexicon<M>) {
  const { language } = useContext(LanguageContext);
  return cachedTranslator<Messages>(
    (lexicon ?? common) as Lexicon<Messages>,
    language,
  );
}
