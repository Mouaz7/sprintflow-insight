import { defineLexicon } from '../i18n/lexicon';

export const offlineText = defineLexicon({
  EN: {
    offline: 'Offline. Showing saved workspace data.',
    pending: ({ count }: { count: number }, format) =>
      format.plural(count, {
        one: '# queued change',
        other: '# queued changes',
      }),
    unavailable:
      'Could not save this change offline. Keep your draft and try again when connected.',
    conflict: 'This task changed on the server.',
    blocked: 'The server could not apply this change.',
    keep: 'Keep server version',
    redo: 'Redo my change',
    discard: 'Discard change',
    retry: 'Sync now',
    busy: 'Syncing changes…',
    failed: 'Could not sync changes. Try again when connected.',
    MOVE: 'Move task',
    COMMENT: 'Post comment',
    LOG_TIME: 'Log time',
  },
  SV: {
    offline: 'Offline. Sparad information från arbetsytan visas.',
    pending: ({ count }: { count: number }, format) =>
      format.plural(count, {
        one: '# köad ändring',
        other: '# köade ändringar',
      }),
    unavailable:
      'Ändringen kunde inte sparas offline. Behåll utkastet och försök igen när du är ansluten.',
    conflict: 'Uppgiften har ändrats på servern.',
    blocked: 'Servern kunde inte genomföra ändringen.',
    keep: 'Behåll serverns version',
    redo: 'Gör om min ändring',
    discard: 'Ta bort ändringen',
    retry: 'Synka nu',
    busy: 'Synkar ändringar…',
    failed: 'Ändringarna kunde inte synkas. Försök igen när du är ansluten.',
    MOVE: 'Flytta uppgift',
    COMMENT: 'Skicka kommentar',
    LOG_TIME: 'Logga tid',
  },
});
