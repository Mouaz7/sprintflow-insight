import * as Y from 'yjs';
import { fromBase64 } from './contracts';

export const contentField = 'content';

/** All peers start with identical Yjs structs for the server's immutable initial text. */
export function seedText(document: Y.Doc, value: string, origin: unknown) {
  const seed = new Y.Doc();
  seed.clientID = 0;
  if (value) seed.getText(contentField).insert(0, value);
  Y.applyUpdate(document, Y.encodeStateAsUpdate(seed), origin);
  seed.destroy();
}

/** Express a normal input edit as one delete and one insert, preserving Unicode boundaries. */
export function changeText(text: Y.Text, input: string) {
  const before = Array.from(text.toString());
  const after = Array.from(input);
  let commonStart = 0;
  while (
    commonStart < before.length &&
    commonStart < after.length &&
    before[commonStart] === after[commonStart]
  )
    commonStart++;
  let commonEnd = 0;
  while (
    commonEnd < before.length - commonStart &&
    commonEnd < after.length - commonStart &&
    before[before.length - commonEnd - 1] ===
      after[after.length - commonEnd - 1]
  )
    commonEnd++;
  const at = before.slice(0, commonStart).join('').length;
  const deleted = before
    .slice(commonStart, before.length - commonEnd)
    .join('').length;
  const inserted = after.slice(commonStart, after.length - commonEnd).join('');
  text.doc?.transact(() => {
    if (deleted) text.delete(at, deleted);
    if (inserted) text.insert(at, inserted);
  });
}

export async function unpackSnapshot(encoded: string): Promise<Uint8Array[]> {
  const compressed = fromBase64(encoded);
  const reader = new Blob([compressed])
    .stream()
    .pipeThrough(new DecompressionStream('gzip'))
    .getReader();
  const raw = new Uint8Array(2 * 1024 * 1024);
  let size = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    if (size + value.length > raw.length) {
      await reader.cancel();
      throw new Error('Shared snapshot exceeds its size limit.');
    }
    raw.set(value, size);
    size += value.length;
  }
  if (size < 4) throw new Error('Truncated shared snapshot.');
  const view = new DataView(raw.buffer, 0, size);
  const count = view.getInt32(0);
  if (count < 0 || count > 16_384)
    throw new Error('Invalid shared snapshot frame count.');
  const frames: Uint8Array[] = [];
  let offset = 4;
  for (let i = 0; i < count; i++) {
    const length = view.getInt32(offset);
    offset += 4;
    if (length < 1 || length > 12 * 1024 || offset + length > size)
      throw new Error('Invalid shared snapshot frame length.');
    frames.push(raw.slice(offset, offset + length));
    offset += length;
  }
  if (offset !== size) throw new Error('Unexpected shared snapshot data.');
  return frames;
}
