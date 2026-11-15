import { describe, expect, it } from 'vitest';
import { backoff, fill, step, type RealtimeMessage } from './sequence';

const message = (seq: number): RealtimeMessage => ({
  projectId: 1,
  seq,
  type: 'TaskMoved',
  entityId: 9,
  version: 2,
});

describe('sequence tracking', () => {
  it('applies the next number, ignores old ones and detects gaps', () => {
    expect(step(4, 5)).toBe('apply');
    expect(step(4, 4)).toBe('duplicate');
    expect(step(4, 2)).toBe('duplicate');
    expect(step(4, 7)).toBe('gap');
    expect(step(undefined, 1)).toBe('gap');
  });

  it('fills a gap with the missed messages only', () => {
    expect(
      fill(4, {
        projectId: 1,
        latestSeq: 6,
        resync: false,
        events: [message(4), message(5), message(6)],
      }),
    ).toEqual({ last: 6, missed: [message(5), message(6)], resync: false });
  });

  it('only learns the position on the first fill', () => {
    expect(
      fill(undefined, {
        projectId: 1,
        latestSeq: 3,
        resync: false,
        events: [message(1), message(2), message(3)],
      }),
    ).toEqual({ last: 3, missed: [], resync: false });
  });

  it('asks for a reload when the server says resync', () => {
    expect(
      fill(4, { projectId: 1, latestSeq: 900, resync: true, events: [] }),
    ).toEqual({ last: 900, missed: [], resync: true });
  });

  it('backs off exponentially up to 30 seconds with jitter', () => {
    expect(backoff(0, () => 0)).toBe(1000);
    expect(backoff(3, () => 0)).toBe(8000);
    expect(backoff(10, () => 0)).toBe(30_000);
    expect(backoff(10, () => 1)).toBe(36_000);
    expect(backoff(-2, () => 0)).toBe(1000);
  });
});
