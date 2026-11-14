import { describe, expect, it } from 'vitest';
import { localizeMessage, roleLabel } from './messages';

describe('Message language', () => {
  it.each([
    [
      'A project with this name already exists.',
      'Det finns redan ett projekt med det här namnet.',
    ],
    ['Current password is incorrect.', 'Ditt nuvarande lösenord är felaktigt.'],
    [
      'Only one sprint can be active in a project at a time.',
      'Endast en sprint kan vara aktiv åt gången i ett projekt.',
    ],
    [
      'This reset link is invalid or has expired.',
      'Återställningslänken är ogiltig eller har gått ut. Begär en ny länk.',
    ],
    [
      'This task was changed by someone else. Refresh and try again.',
      'Informationen har ändrats av någon annan. Uppdatera sidan och försök igen.',
    ],
    [
      '3 blocked task(s) need attention.',
      '3 blockerade uppgifter behöver åtgärdas.',
    ],
  ])('translates %s and preserves the original English', (english, swedish) => {
    expect(localizeMessage(english, 'SV')).toBe(swedish);
    expect(localizeMessage(english, 'EN')).toBe(english);
  });
  it('preserves unknown messages and user content', () => {
    expect(localizeMessage('My custom text', 'SV')).toBe('My custom text');
  });
  it('uses Swedish role labels while preserving API values', () => {
    expect(roleLabel('Owner', 'SV')).toBe('Ägare');
    expect(roleLabel('Member', 'SV')).toBe('Medlem');
    expect(roleLabel('Viewer', 'EN')).toBe('Viewer');
  });
});
