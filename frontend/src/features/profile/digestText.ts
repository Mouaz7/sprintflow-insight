import { defineLexicon } from '../../shared/i18n/lexicon';
export const digestText = defineLexicon({
  EN: {
    title: 'Email summaries',
    help: 'Receive unread notifications you can still access. Daily summaries arrive at 08:00; weekly summaries on Monday at 08:00 in your chosen time zone. Up to 50 updates are included. Summaries pause while notifications are turned off.',
    frequency: 'Summary frequency',
    OFF: 'Off',
    DAILY: 'Daily',
    WEEKLY: 'Weekly',
    zone: 'Time zone (IANA)',
    save: 'Save email summaries',
    saving: 'Saving…',
    saved: 'Email summary settings saved.',
    loading: 'Loading email summary settings…',
    failed: 'Could not load email summary settings.',
    saveFailed: 'Could not save settings. Your draft is still here.',
    conflict:
      'These settings changed in another window. Your draft is still here. Load the saved settings before changing them again.',
    reload: 'Load saved settings',
    notConfigured:
      'Email is not configured. You can save your preference, but no summaries will be sent until email is available.',
    next: 'Next summary',
    last: 'Last summary',
    date: ({ value, zone }: { value: string; zone: string }, f) =>
      f.dateTime(value, { timeZone: zone, timeZoneName: 'short' }),
    unsubscribe: 'Stop email summaries',
    unsubscribeHelp:
      'Confirm to stop daily and weekly email summaries. Your in-app notifications will stay available.',
    confirm: 'Stop email summaries',
    stopped: 'Email summaries have been stopped.',
    invalid:
      'This unsubscribe link is invalid or has expired. You can change your settings in your profile.',
    unsubscribeFailed: 'Could not update your preference. Try again.',
    profile: 'Open profile',
    language: 'Language',
  },
  SV: {
    title: 'E-postsammanfattningar',
    help: 'Få olästa aviseringar som du fortfarande har åtkomst till. Dagliga sammanfattningar skickas klockan 08.00; veckovisa på måndagar klockan 08.00 i din valda tidszon. Högst 50 uppdateringar tas med. Sammanfattningar pausas när aviseringar är avstängda.',
    frequency: 'Sammanfattningsintervall',
    OFF: 'Av',
    DAILY: 'Dagligen',
    WEEKLY: 'Veckovis',
    zone: 'Tidszon (IANA)',
    save: 'Spara e-postsammanfattningar',
    saving: 'Sparar…',
    saved: 'Inställningarna för e-postsammanfattningar har sparats.',
    loading: 'Laddar inställningar för e-postsammanfattningar…',
    failed: 'Kunde inte ladda inställningarna för e-postsammanfattningar.',
    saveFailed: 'Kunde inte spara inställningarna. Ditt utkast finns kvar.',
    conflict:
      'Inställningarna har ändrats i ett annat fönster. Ditt utkast finns kvar. Läs in de sparade inställningarna innan du ändrar dem igen.',
    reload: 'Läs in sparade inställningar',
    notConfigured:
      'E-post är inte konfigurerad. Du kan spara ditt val, men inga sammanfattningar skickas innan e-post är tillgänglig.',
    next: 'Nästa sammanfattning',
    last: 'Senaste sammanfattningen',
    date: ({ value, zone }, f) =>
      f.dateTime(value, { timeZone: zone, timeZoneName: 'short' }),
    unsubscribe: 'Stoppa e-postsammanfattningar',
    unsubscribeHelp:
      'Bekräfta för att stoppa dagliga och veckovisa e-postsammanfattningar. Aviseringarna i appen finns kvar.',
    confirm: 'Stoppa e-postsammanfattningar',
    stopped: 'E-postsammanfattningar har stoppats.',
    invalid:
      'Avregistreringslänken är ogiltig eller har gått ut. Du kan ändra inställningarna i din profil.',
    unsubscribeFailed: 'Kunde inte uppdatera ditt val. Försök igen.',
    profile: 'Öppna profil',
    language: 'Språk',
  },
});
