/** One realtime message, as sent by the server and the gap endpoint. */
export type RealtimeMessage = {
  projectId: number;
  seq: number;
  type: string;
  entityId: number | null;
  version: number | null;
};

export type EventPage = {
  projectId: number;
  latestSeq: number;
  resync: boolean;
  events: RealtimeMessage[];
};

export type Step = 'apply' | 'duplicate' | 'gap';

/** What to do with `seq` after `last` was applied (undefined: unknown yet). */
export function step(last: number | undefined, seq: number): Step {
  if (last === undefined) return 'gap';
  if (seq <= last) return 'duplicate';
  return seq === last + 1 ? 'apply' : 'gap';
}

/**
 * Applies a gap page on top of `last`. Returns the new position and the
 * messages that were missed, or `resync` when the client must reload.
 */
export function fill(
  last: number | undefined,
  page: EventPage,
): { last: number; missed: RealtimeMessage[]; resync: boolean } {
  if (page.resync) return { last: page.latestSeq, missed: [], resync: true };
  const missed =
    last === undefined ? [] : page.events.filter((event) => event.seq > last);
  return {
    last: Math.max(last ?? 0, page.latestSeq),
    missed,
    resync: false,
  };
}

/** Exponential reconnect delay: 1 s doubling to 30 s, plus up to 20 % jitter. */
export function backoff(attempt: number, random = Math.random): number {
  const base = Math.min(30_000, 1000 * 2 ** Math.max(0, attempt));
  return Math.round(base * (1 + 0.2 * random()));
}
