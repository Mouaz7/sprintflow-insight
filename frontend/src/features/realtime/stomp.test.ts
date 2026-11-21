import { describe, expect, it } from 'vitest';
import {
  encodeBinaryFrame,
  encodeFrame,
  parseBinaryFrames,
  parseFrames,
} from '../../shared/lib/stomp';

describe('STOMP frames', () => {
  it('encodes headers with escaping except on CONNECT', () => {
    expect(encodeFrame('SUBSCRIBE', { id: 'a:b', destination: '/t\n' })).toBe(
      'SUBSCRIBE\nid:a\\cb\ndestination:/t\\n\n\n\0',
    );
    expect(encodeFrame('CONNECT', { host: 'h:1' })).toBe(
      'CONNECT\nhost:h:1\n\n\0',
    );
    expect(encodeFrame('DISCONNECT')).toBe('DISCONNECT\n\n\n\0');
  });

  it('parses several frames, heart-beats and a partial tail', () => {
    const text =
      '\nMESSAGE\nsubscription:events\ndestination:/topic/projects/1\n\n{"seq":1}\0' +
      '\r\n\r\nCONNECTED\r\nversion:1.2\r\n\r\n\0ERR';
    const { frames, rest } = parseFrames(text);
    expect(frames).toEqual([
      {
        command: 'MESSAGE',
        headers: new Map([
          ['subscription', 'events'],
          ['destination', '/topic/projects/1'],
        ]),
        body: '{"seq":1}',
      },
      {
        command: 'CONNECTED',
        headers: new Map([['version', '1.2']]),
        body: '',
      },
    ]);
    expect(rest).toBe('ERR');
  });

  it('unescapes header values and keeps the first repeated header', () => {
    const { frames } = parseFrames(
      'ERROR\nmessage:a\\cb\\\\c\\nd\nmessage:second\nbroken\n\n\0',
    );
    expect(frames[0]?.headers).toEqual(new Map([['message', 'a:b\\c\nd']]));
  });

  it('accepts frames without a blank line', () => {
    expect(parseFrames('RECEIPT\0').frames).toEqual([
      { command: 'RECEIPT', headers: new Map(), body: '' },
    ]);
  });

  it('keeps prototype-like header names as plain data', () => {
    const { frames } = parseFrames('MESSAGE\n__proto__:x\nconstructor:y\n\n\0');
    expect(Object.getPrototypeOf({})).not.toHaveProperty('x');
    expect(frames[0]?.headers.get('__proto__')).toBe('x');
    expect(frames[0]?.headers.get('constructor')).toBe('y');
  });
});

it('keeps binary NULs intact across a split STOMP frame', () => {
  const body = new Uint8Array([0, 10, 0, 255, 13, 2]);
  const encoded = new Uint8Array(
    encodeBinaryFrame('MESSAGE', { subscription: 'shared-updates' }, body),
  );
  const first = parseBinaryFrames(encoded.subarray(0, encoded.length - 3));
  expect(first.frames).toHaveLength(0);
  const completed = new Uint8Array(encoded.length + 1);
  completed.set(first.rest);
  completed.set(encoded.subarray(encoded.length - 3), first.rest.length);
  completed[encoded.length] = 10;
  const result = parseBinaryFrames(completed);
  expect(result.frames[0]?.body).toEqual(body);
  expect(result.frames[0]?.headers.get('subscription')).toBe('shared-updates');
  expect(result.rest).toHaveLength(0);
});
