import { defineLexicon } from './lexicon';

/** Words shared by several features; feature text lives in the feature's `i18n.ts`. */
export const common = defineLexicon({
  EN: {
    loading: 'Loading…',
    cancel: 'Cancel',
    save: 'Save',
    close: 'Close',
    skipToContent: 'Skip to content',
    switchToLight: 'Switch to light mode',
    switchToDark: 'Switch to dark mode',
    language: 'Language',
    changeLanguage: 'Change language: English',
  },
  SV: {
    loading: 'Laddar…',
    cancel: 'Avbryt',
    save: 'Spara',
    close: 'Stäng',
    skipToContent: 'Hoppa till innehåll',
    switchToLight: 'Byt till ljust läge',
    switchToDark: 'Byt till mörkt läge',
    language: 'Språk',
    changeLanguage: 'Byt språk: Svenska',
  },
});
