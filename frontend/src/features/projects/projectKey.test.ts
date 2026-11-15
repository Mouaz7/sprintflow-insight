import { describe, expect, it } from 'vitest';
import {
  deriveProjectKey,
  isValidProjectKey,
  normalizeProjectKey,
} from './projectKey';

describe('project keys', () => {
  it('derives the same keys as the server', () => {
    expect(deriveProjectKey('Sprint Flow')).toBe('SF');
    expect(deriveProjectKey('sprintflow-insight web app v2')).toBe('SIWA');
    expect(deriveProjectKey('Apollo')).toBe('APOL');
    expect(deriveProjectKey('Åre Östra')).toBe('AO');
    expect(deriveProjectKey('Ærø Ørsted')).toBe('AO');
    expect(deriveProjectKey('Café')).toBe('CAFE');
    for (const name of ['', '   ', '2026', 'A', '🚀 7']) {
      expect(deriveProjectKey(name)).toBe('PRJ');
    }
  });

  it('accepts 2–10 letters in either case', () => {
    expect(isValidProjectKey('sf')).toBe(true);
    expect(isValidProjectKey(' ABCDEFGHIJ ')).toBe(true);
    for (const bad of ['', 'S', 'S1', 'ABCDEFGHIJK', 'S-F', 'ÅÄ']) {
      expect(isValidProjectKey(bad)).toBe(false);
    }
    expect(normalizeProjectKey(' sf ')).toBe('SF');
  });
});
