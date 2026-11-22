import type { Language } from '../../api/contracts';

export type Theme = 'light' | 'dark';

const languageKey = 'sprintflow-language';
const themeKey = 'sprintflow-theme';
const themeColors: Record<Theme, string> = {
  light: '#FAF8F5',
  dark: '#151D2A',
};

function read(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function write(key: string, value: string) {
  try {
    localStorage.setItem(key, value);
  } catch {
    // Appearance still applies for this page when storage is unavailable.
  }
}

export function storedLanguage(): Language {
  return read(languageKey) === 'SV' ? 'SV' : 'EN';
}

export function storedTheme(): Theme {
  return read(themeKey) === 'dark' ? 'dark' : 'light';
}

/** Applies the language to the document; `persist` also remembers it. */
export function applyLanguage(language: Language, persist = true) {
  if (persist) write(languageKey, language);
  document.documentElement.lang = language === 'SV' ? 'sv' : 'en';
}

export function applyTheme(theme: Theme, persist = true) {
  if (persist) write(themeKey, theme);
  document.documentElement.dataset.theme = theme;
  document
    .querySelector('meta[name="theme-color"]')
    ?.setAttribute('content', themeColors[theme]);
}
