/**
 * The few STOMP 1.2 frames SprintFlow needs, encoded and parsed by hand.
 * A full client library would add more to the bundle than the whole
 * realtime feature.
 */
export type StompFrame = {
  command: string;
  headers: Record<string, string>;
  body: string;
};

const escapes: Record<string, string> = {
  '\\\\': '\\',
  '\\n': '\n',
  '\\r': '\r',
  '\\c': ':',
};

function escapeHeader(value: string): string {
  return value
    .replace(/\\/g, '\\\\')
    .replace(/\n/g, '\\n')
    .replace(/\r/g, '\\r')
    .replace(/:/g, '\\c');
}

function unescapeHeader(value: string): string {
  return value.replace(/\\[\\nrc]/g, (sequence) => escapes[sequence] ?? '');
}

export function encodeFrame(
  command: string,
  headers: Record<string, string> = {},
  body = '',
): string {
  // CONNECT headers are not escaped in STOMP 1.2 (spec, "Value Encoding").
  const encode = command === 'CONNECT' ? (v: string) => v : escapeHeader;
  const lines = Object.entries(headers).map(
    ([name, value]) => `${encode(name)}:${encode(value)}`,
  );
  return `${command}\n${lines.join('\n')}\n\n${body}\0`;
}

/**
 * Splits received text into complete frames. Heart-beat end-of-lines between
 * frames are skipped; an incomplete trailing frame is returned as `rest`.
 */
export function parseFrames(buffer: string): {
  frames: StompFrame[];
  rest: string;
} {
  const frames: StompFrame[] = [];
  let rest = buffer;
  for (;;) {
    rest = rest.replace(/^(\r?\n)+/, '');
    const end = rest.indexOf('\0');
    if (end < 0) break;
    const raw = rest.slice(0, end);
    rest = rest.slice(end + 1);
    const split = raw.search(/\r?\n\r?\n/);
    const head = split < 0 ? raw : raw.slice(0, split);
    const body = split < 0 ? '' : raw.slice(split).replace(/^\r?\n\r?\n/, '');
    const [command = '', ...lines] = head.split(/\r?\n/);
    const headers: Record<string, string> = {};
    for (const line of lines) {
      const colon = line.indexOf(':');
      if (colon <= 0) continue;
      const name = unescapeHeader(line.slice(0, colon));
      // The first occurrence of a repeated header wins (STOMP 1.2).
      if (!(name in headers))
        headers[name] = unescapeHeader(line.slice(colon + 1));
    }
    frames.push({ command, headers, body });
  }
  return { frames, rest };
}
