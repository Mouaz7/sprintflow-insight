/**
 * The few STOMP 1.2 frames SprintFlow needs, encoded and parsed by hand.
 * A full client library would add more to the bundle than the whole
 * realtime feature.
 */
export type StompFrame = {
  command: string;
  headers: ReadonlyMap<string, string>;
  body: string;
};

export type BinaryStompFrame = Omit<StompFrame, 'body'> & {
  body: Uint8Array;
};

const textEncoder = new TextEncoder();
const textDecoder = new TextDecoder();

export function realtimeUrl(location: Location = window.location): string {
  return `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`;
}

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
    // A Map, so header names from the network never become object properties.
    const headers = new Map<string, string>();
    for (const line of lines) {
      const colon = line.indexOf(':');
      if (colon <= 0) continue;
      const name = unescapeHeader(line.slice(0, colon));
      // The first occurrence of a repeated header wins (STOMP 1.2).
      if (!headers.has(name))
        headers.set(name, unescapeHeader(line.slice(colon + 1)));
    }
    frames.push({ command, headers, body });
  }
  return { frames, rest };
}

/** The length header counts bytes, so an update may contain NUL without closing the frame. */
export function encodeBinaryFrame(
  command: string,
  headers: Record<string, string>,
  body: Uint8Array,
): ArrayBuffer {
  const head = textEncoder.encode(
    encodeFrame(command, {
      ...headers,
      'content-length': String(body.length),
    }).slice(0, -1),
  );
  const frame = new Uint8Array(head.length + body.length + 1);
  frame.set(head);
  frame.set(body, head.length);
  return frame.buffer;
}

function boundary(
  buffer: Uint8Array,
  start: number,
): { head: number; body: number } | undefined {
  for (let i = start; i < buffer.length - 1; i++) {
    if (buffer[i] !== 10) continue;
    if (buffer[i + 1] === 10) return { head: i, body: i + 2 };
    if (buffer[i + 1] === 13 && buffer[i + 2] === 10)
      return { head: buffer[i - 1] === 13 ? i - 1 : i, body: i + 3 };
  }
  return undefined;
}

export function parseBinaryFrames(buffer: Uint8Array): {
  frames: BinaryStompFrame[];
  rest: Uint8Array;
} {
  if (buffer.length > 512 * 1024)
    throw new Error('STOMP input exceeded the buffer limit.');
  const frames: BinaryStompFrame[] = [];
  let start = 0;
  while (start < buffer.length) {
    while (buffer[start] === 10 || buffer[start] === 13) start++;
    const split = boundary(buffer, start);
    if (!split) break;
    const [frame] = parseFrames(
      `${textDecoder.decode(buffer.subarray(start, split.head))}\0`,
    ).frames;
    if (!frame) throw new Error('Invalid STOMP frame header.');
    const declared = frame.headers.get('content-length');
    if (declared !== undefined && !/^\d{1,6}$/.test(declared))
      throw new Error('Invalid STOMP content length.');
    const end =
      declared === undefined
        ? buffer.indexOf(0, split.body)
        : split.body + Number(declared);
    if (end < 0 || end >= buffer.length) break;
    if (buffer[end] !== 0) throw new Error('Missing STOMP frame terminator.');
    frames.push({
      command: frame.command,
      headers: frame.headers,
      body: buffer.slice(split.body, end),
    });
    start = end + 1;
  }
  return { frames, rest: buffer.slice(start) };
}
