import { defineLexicon } from '../../shared/i18n/lexicon';
export const taskFieldsText = defineLexicon({
  EN: {
    title: 'Task custom fields',
    loading: 'Loading task fields…',
    failed: 'Could not load task fields. Try again.',
    readonly:
      'You can read these values. Changes require write access to an active project.',
    empty: 'No custom fields are configured for this project.',
    save: 'Save values',
    saving: 'Saving…',
    cancel: 'Cancel',
    reload: 'Reload saved values',
    changed:
      'The task changed since this draft was opened. Reload saved values to review the changes.',
    conflict:
      'The task changed. Your draft is kept. Reload saved values before applying it again.',
    saveFailed: 'Could not save these values. Check them and try again.',
    projectFields: 'Project custom fields',
  },
  SV: {
    title: 'Uppgiftens anpassade fält',
    loading: 'Laddar uppgiftsfält…',
    failed: 'Kunde inte läsa uppgiftsfälten. Försök igen.',
    readonly:
      'Du kan läsa värdena. Ändringar kräver skrivbehörighet i ett aktivt projekt.',
    empty: 'Inga anpassade fält är konfigurerade för projektet.',
    save: 'Spara värden',
    saving: 'Sparar…',
    cancel: 'Avbryt',
    reload: 'Läs in sparade värden',
    changed:
      'Uppgiften har ändrats sedan utkastet öppnades. Läs in sparade värden för att granska ändringarna.',
    conflict:
      'Uppgiften har ändrats. Ditt utkast finns kvar. Läs in sparade värden innan du försöker spara igen.',
    saveFailed: 'Kunde inte spara värdena. Kontrollera dem och försök igen.',
    projectFields: 'Projektets anpassade fält',
  },
});
