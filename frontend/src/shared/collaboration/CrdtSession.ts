import * as Y from 'yjs';
import { api, ApiError } from '../../api/client';
import { CrdtSocket } from './CrdtSocket';
import {
  collaborationApi,
  fromBase64,
  toBase64,
  type Caret,
  type CollaborationScope,
  type Presence,
  type Snapshot,
} from './contracts';
import { changeText, contentField, seedText, unpackSnapshot } from './text';

const remote = Symbol('remote collaboration update');
const heartbeatMs = 10_000;
const maxUpdateBytes = 12 * 1024;
const maxPending = 1024;
const emptyCaret: Caret = { anchor: null, head: null };

export type SessionView = {
  text: string;
  active: boolean;
  editable: boolean;
  maxLength: number;
  ready: boolean;
  connected: boolean;
  unsaved: boolean;
  invalid: boolean;
  error: string;
  presence: { id: string; name: string; anchor: number; head: number }[];
};

export const emptyView: SessionView = {
  text: '',
  active: false,
  editable: false,
  maxLength: 0,
  ready: false,
  connected: false,
  unsaved: false,
  invalid: false,
  error: '',
  presence: [],
};

export class CrdtSession {
  private readonly document = new Y.Doc();
  private readonly text = this.document.getText(contentField);
  private readonly pending = new Map<string, Uint8Array>();
  private readonly arrivals = new Set<number>();
  private readonly people = new Map<string, Presence & { seen: number }>();
  private readonly path: string;
  private readonly socket: CrdtSocket;
  private readonly abort = new AbortController();
  private readonly interval: ReturnType<typeof setInterval>;
  private state: Snapshot | undefined;
  private revision = 0;
  private projected = 0;
  private connected = false;
  private refreshing = false;
  private projecting = false;
  private closed = false;
  private error = '';
  private caret: Caret = emptyCaret;
  private caretTimer: ReturnType<typeof setTimeout> | undefined;
  private lastPresence = 0;

  constructor(
    private readonly scope: CollaborationScope,
    private readonly userEmail: string,
    private readonly render: (view: SessionView) => void,
    private readonly onProjected: () => void,
  ) {
    this.path = collaborationApi(scope);
    this.socket = new CrdtSocket(scope, {
      connected: () => {
        this.connected = true;
        this.tick();
      },
      disconnected: () => {
        this.connected = false;
        this.publish();
      },
      update: (rev, id, update) => this.receive(rev, id, update),
      awareness: (presence) => {
        if (presence.name.toLowerCase() === this.userEmail.toLowerCase())
          return;
        if (presence.anchor && presence.head)
          this.people.set(presence.sessionId, {
            ...presence,
            seen: Date.now(),
          });
        else this.people.delete(presence.sessionId);
        this.publish();
      },
    });
    this.document.on('update', (update: Uint8Array, origin: unknown) => {
      if (origin === remote) return;
      const id = crypto.randomUUID().replaceAll('-', '');
      this.pending.set(id, update);
      this.socket.send(id, update);
      this.publish();
    });
    this.interval = setInterval(() => this.tick(), 4000);
    void this.refresh();
  }

  close() {
    this.closed = true;
    this.abort.abort();
    clearInterval(this.interval);
    clearTimeout(this.caretTimer);
    this.socket.sendCaret(emptyCaret);
    this.socket.shutDown();
    this.document.destroy();
  }

  async activate() {
    if (this.closed || this.refreshing || !this.state?.editable) return;
    this.refreshing = true;
    try {
      const result = await api<Snapshot>(this.path, {
        method: 'POST',
        signal: this.abort.signal,
      });
      await this.load(result);
      this.error = '';
    } catch (cause) {
      this.failure(cause);
    } finally {
      this.refreshing = false;
      this.publish();
    }
  }

  change(value: string) {
    if (
      !this.state?.editable ||
      !this.state.active ||
      this.pending.size >= maxPending
    )
      return;
    const previous = this.text.length;
    if (value.length > this.state.maxLength && value.length >= previous) return;
    changeText(this.text, value);
  }

  select(start: number, end: number) {
    if (!this.state?.editable || !this.state.active) return;
    const encode = (offset: number) =>
      toBase64(
        Y.encodeRelativePosition(
          Y.createRelativePositionFromTypeIndex(
            this.text,
            Math.min(offset, this.text.length),
          ),
        ),
      );
    this.caret = { anchor: encode(start), head: encode(end) };
    this.scheduleCaret();
  }

  clearSelection() {
    this.caret = emptyCaret;
    this.scheduleCaret();
  }

  private scheduleCaret() {
    if (this.caretTimer !== undefined) return;
    this.caretTimer = setTimeout(() => {
      this.caretTimer = undefined;
      this.sendCaret();
    }, 120);
  }

  private sendCaret() {
    if (!this.connected || !this.state?.editable) return;
    this.lastPresence = Date.now();
    this.socket.sendCaret(this.caret);
  }

  private tick() {
    for (const [id, person] of this.people) {
      if (Date.now() - person.seen > 3 * heartbeatMs) this.people.delete(id);
    }
    if (Date.now() - this.lastPresence >= heartbeatMs && this.caret.head)
      this.sendCaret();
    for (const [id, update] of this.pending) this.socket.send(id, update);
    void this.refresh();
    this.publish();
  }

  private async refresh() {
    if (this.closed || this.refreshing) return;
    this.refreshing = true;
    try {
      const result = await api<Snapshot>(
        `${this.path}?after=${this.revision}`,
        {
          signal: this.abort.signal,
        },
      );
      await this.load(result);
      this.error = '';
    } catch (cause) {
      this.failure(cause);
    } finally {
      this.refreshing = false;
      this.publish();
    }
    void this.project();
  }

  private async load(state: Snapshot) {
    if (!this.state?.active && state.active)
      seedText(this.document, state.initialText, remote);
    const frames =
      state.snapshot && state.snapshotRevision > this.revision
        ? await unpackSnapshot(state.snapshot)
        : [];
    if (this.closed) return;
    this.document.transact(() => {
      for (const frame of frames) this.apply(frame);
      if (frames.length)
        this.revision = Math.max(this.revision, state.snapshotRevision);
      for (const update of state.updates) {
        if (
          update.revision <= this.revision ||
          this.arrivals.has(update.revision)
        )
          continue;
        this.apply(fromBase64(update.bytes));
        this.arrivals.add(update.revision);
      }
    }, remote);
    for (const seen of this.arrivals)
      if (seen <= this.revision) this.arrivals.delete(seen);
    this.advance();
    this.projected = Math.max(this.projected, state.projectionRevision);
    const wasActive = this.state?.active;
    this.state = state;
    if (state.active && !wasActive) this.socket.open();
  }

  private receive(revision: number, updateId: string, update: Uint8Array) {
    if (revision > this.revision && !this.arrivals.has(revision)) {
      this.apply(update);
      this.arrivals.add(revision);
      this.advance();
    }
    this.pending.delete(updateId);
    this.publish();
    if (this.arrivals.size) void this.refresh();
    else void this.project();
  }

  private apply(update: Uint8Array) {
    if (update.length > maxUpdateBytes)
      throw new Error('Invalid shared update size.');
    try {
      Y.applyUpdate(this.document, update, remote);
    } catch {
      // One invalid peer frame must not prevent reading subsequent stored revisions.
    }
  }

  private advance() {
    while (this.arrivals.delete(this.revision + 1)) this.revision++;
  }

  private invalid() {
    return (
      this.text.length > (this.state?.maxLength ?? 0) ||
      (this.scope.target === 'RETROSPECTIVE' &&
        this.text.toString().trim() === '')
    );
  }

  private async project() {
    if (
      this.closed ||
      this.projecting ||
      this.refreshing ||
      !this.state?.editable ||
      !this.state.active ||
      this.pending.size ||
      this.arrivals.size ||
      this.revision <= this.projected ||
      this.invalid()
    )
      return;
    this.projecting = true;
    const revision = this.revision;
    try {
      await api(`${this.path}/projection`, {
        method: 'POST',
        body: JSON.stringify({ revision, text: this.text.toString() }),
        signal: this.abort.signal,
      });
      if (this.closed) return;
      this.projected = Math.max(this.projected, revision);
      this.onProjected();
    } catch (cause) {
      if (!(cause instanceof ApiError && cause.status === 409))
        this.failure(cause);
    } finally {
      this.projecting = false;
      this.publish();
    }
  }

  private failure(cause: unknown) {
    this.error = cause instanceof Error ? cause.message : '';
    if (
      this.state &&
      cause instanceof ApiError &&
      [401, 403, 404].includes(cause.status)
    ) {
      this.state = { ...this.state, editable: false };
    }
  }

  private cursor(encoded: string | null): number | undefined {
    if (!encoded) return;
    try {
      const relative = Y.decodeRelativePosition(fromBase64(encoded));
      const caret = Y.createAbsolutePositionFromRelativePosition(
        relative,
        this.document,
      );
      if (caret?.type === this.text) return caret.index;
    } catch {
      // Presence is ephemeral and may refer to a revision still in transit.
    }
  }

  private publish() {
    if (this.closed) return;
    const presence = [];
    for (const [id, person] of this.people) {
      const anchor = this.cursor(person.anchor);
      const head = this.cursor(person.head);
      if (anchor !== undefined && head !== undefined)
        presence.push({ id, name: person.name, anchor, head });
    }
    this.render({
      text: this.state?.active
        ? this.text.toString()
        : (this.state?.initialText ?? ''),
      active: this.state?.active ?? false,
      editable: this.state?.editable ?? false,
      maxLength: this.state?.maxLength ?? 0,
      ready: !!this.state,
      connected: this.connected,
      unsaved: this.pending.size > 0,
      invalid: !!this.state?.active && this.invalid(),
      error: this.error,
      presence,
    });
  }
}
