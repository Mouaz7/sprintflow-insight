import { describe, expect, it } from 'vitest';
import * as Y from 'yjs';
import { changeText, contentField, seedText } from './text';

function random(seed: number) {
  let value = seed >>> 0;
  return (limit: number) => {
    value = (Math.imul(1664525, value) + 1013904223) >>> 0;
    return Math.floor((value / 0x1_0000_0000) * limit);
  };
}

describe('shared text', () => {
  it('preserves Unicode characters when a selection changes a neighbouring character', () => {
    const first = new Y.Doc();
    const second = new Y.Doc();
    seedText(first, 'a🚀 ö b', null);
    seedText(second, 'a🚀 ö b', null);
    let change: Uint8Array | undefined;
    first.on('update', (update: Uint8Array) => (change = update));
    changeText(first.getText(contentField), 'a🚀 ø b');
    if (!change) throw new Error('Expected a local update.');
    Y.applyUpdate(second, change);
    expect(second.getText(contentField).toString()).toBe('a🚀 ø b');
    first.destroy();
    second.destroy();
  });

  it('converges with concurrent, reordered and repeated updates (seed 20261121)', () => {
    const choose = random(20261121);
    for (let trial = 0; trial < 64; trial++) {
      const documents = Array.from({ length: 3 }, (_, index) => {
        const doc = new Y.Doc();
        doc.clientID = index + 1;
        seedText(doc, 'sprint', null);
        return doc;
      });
      const updates: Uint8Array[] = [];
      for (const doc of documents) {
        doc.on('update', (update: Uint8Array, origin: unknown) => {
          if (origin === null) updates.push(update);
        });
        const text = doc.getText(contentField);
        for (let step = 0; step < 24; step++) {
          const at = choose(text.length + 1);
          if (at < text.length && choose(3) === 0) text.delete(at, 1);
          else text.insert(at, String.fromCharCode(97 + choose(26)));
        }
      }
      for (const doc of documents) {
        const order = [...updates];
        while (order.length) {
          const [update] = order.splice(choose(order.length), 1);
          if (!update) throw new Error('Expected a delivered update.');
          Y.applyUpdate(doc, update, 'network');
          Y.applyUpdate(doc, update, 'network');
        }
      }
      const [first, second, third] = documents;
      if (!first || !second || !third)
        throw new Error('Expected three collaborators.');
      const expected = first.getText(contentField).toString();
      expect(second.getText(contentField).toString()).toBe(expected);
      expect(third.getText(contentField).toString()).toBe(expected);
      expect(Y.encodeStateAsUpdate(second)).toEqual(
        Y.encodeStateAsUpdate(first),
      );
      expect(Y.encodeStateAsUpdate(third)).toEqual(
        Y.encodeStateAsUpdate(first),
      );
      for (const doc of documents) doc.destroy();
    }
  });
});
