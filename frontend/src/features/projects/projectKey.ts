// Mirrors ProjectKeys.java so the form can preview the key the server will derive.
// The server stays authoritative: it adds a letter suffix when the key is already taken.
export const PROJECT_KEY_PATTERN = /^[A-Z]{2,10}$/;
export const PROJECT_KEY_MESSAGE = 'Project key must be 2–10 letters A–Z.';

export function normalizeProjectKey(value: string): string {
  return value.trim().toUpperCase();
}

export function isValidProjectKey(value: string): boolean {
  return PROJECT_KEY_PATTERN.test(normalizeProjectKey(value));
}

export function deriveProjectKey(name: string): string {
  const words = name
    .normalize('NFD')
    .replace(/\p{M}+/gu, '')
    .toUpperCase()
    .replace(/Æ/g, 'A')
    .replace(/Ø/g, 'O')
    .split(/[^A-Z]+/)
    .filter(Boolean);
  const key =
    words.length >= 2
      ? words
          .slice(0, 4)
          .map((word) => word[0])
          .join('')
      : (words[0] ?? '').slice(0, 4);
  return key.length >= 2 ? key : 'PRJ';
}
