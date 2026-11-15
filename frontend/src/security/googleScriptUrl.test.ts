import { afterEach, describe, expect, it, vi } from 'vitest';
import { googleScriptUrl } from './googleScriptUrl';

afterEach(() => vi.unstubAllGlobals());

describe('Google script Trusted Types policy', () => {
  it('loads the exact official client in browsers without Trusted Types', () => {
    vi.stubGlobal('trustedTypes', undefined);
    expect(googleScriptUrl()).toBe('https://accounts.google.com/gsi/client');
  });
  it('creates only a URL policy, reuses it and rejects every other URL', () => {
    const branded = { toString: () => 'https://accounts.google.com/gsi/client' };
    const createScriptURL = vi.fn(() => branded);
    const createPolicy = vi.fn((_name: string, _rules: { createScriptURL: (value: string) => string }) => ({ createScriptURL }));
    vi.stubGlobal('trustedTypes', { createPolicy });
    expect(googleScriptUrl()).toBe(branded);
    expect(googleScriptUrl()).toBe(branded);
    expect(createPolicy).toHaveBeenCalledTimes(1);
    expect(createPolicy).toHaveBeenCalledWith('sprintflow-google', {
      createScriptURL: expect.any(Function),
    });
    const rules = createPolicy.mock.calls[0][1] as unknown as {
      createScriptURL: (value: string) => string;
    };
    expect(rules.createScriptURL('https://accounts.google.com/gsi/client')).toBe(
      'https://accounts.google.com/gsi/client',
    );
    for (const url of [
      'javascript:alert(1)',
      'https://evil.example/script.js',
      'https://accounts.google.com/gsi/client?unexpected=1',
      'https://accounts.google.com.evil.example/gsi/client',
      'https://accounts.google.com/gsi/other',
    ]) expect(() => rules.createScriptURL(url)).toThrow(TypeError);
  });
});
