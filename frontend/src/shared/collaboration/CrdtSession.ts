import * as Y from 'yjs';
import { api, ApiError } from '../../api/client';
import { fetchSessionUser } from '../../api/queries';
import {
  offlineAccount,
  updateOfflineAccount,
  type LocalDocument,
} from '../offline/storage';
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
  localSaved: boolean;
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
  localSaved: false,
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
  private projectTimer: ReturnType<typeof setTimeout> | undefined;
  private closed = false;
  private error = '';
  private caret: Caret = emptyCaret;
  private caretTimer: ReturnType<typeof setTimeout> | undefined;
  private lastPresence = 0;
  private localSaved = false;
  private saveGeneration = 0;
  private connectionGeneration = 0;
  private readonly backOnline = () => this.tick();

  constructor(
    private readonly scope: CollaborationScope,
    private readonly userEmail: string,
    private readonly render: (view: SessionView) => void,
    private readonly onProjected: () => void,
    private readonly accountId?: number,
  ) {
    this.path = collaborationApi(scope);
    this.socket = new CrdtSocket(scope, {
      connected: () => {
        void this.confirmConnection();
      },
      disconnected: () => {
        this.connectionGeneration++;
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
      this.localSaved = false;
      this.persistLocal();
      if (this.connected && navigator.onLine) this.socket.send(id, update);
      this.publish();
    });
    this.interval = setInterval(() => this.tick(), 4000);
    window.addEventListener('online', this.backOnline);
    void this.refresh();
  }

  close() {
    this.closed = true;
    this.abort.abort();
    window.removeEventListener('online', this.backOnline);
    clearInterval(this.interval);
    clearTimeout(this.caretTimer);
    clearTimeout(this.projectTimer);
    if (this.state?.editable) this.socket.sendCaret(emptyCaret);
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

  private async confirmConnection() {
    const generation = ++this.connectionGeneration;
    const user =
      this.accountId === undefined
        ? undefined
        : await fetchSessionUser().catch(() => undefined);
    if (this.closed || generation !== this.connectionGeneration) return;
    if (this.accountId !== undefined && user?.id !== this.accountId) return;
    this.connected = true;
    this.tick();
  }

  private tick() {
    for (const [id, person] of this.people) {
      if (Date.now() - person.seen > 3 * heartbeatMs) this.people.delete(id);
    }
    if (Date.now() - this.lastPresence >= heartbeatMs && this.caret.head)
      this.sendCaret();
    if (this.state?.editable && this.connected && navigator.onLine) {
      for (const [id, update] of this.pending) this.socket.send(id, update);
    }
    void this.refresh();
    this.publish();
  }

  private async refresh() {
    if (this.closed || this.refreshing) return;
    this.refreshing = true;
    let cached: LocalDocument | undefined;
    try {
      if (!this.state && this.accountId !== undefined) {
        const local = await offlineAccount().catch(() => undefined);
        if (local?.user.id === this.accountId)
          cached = local.documents[this.path];
      }
      const result = await api<Snapshot>(
        `${this.path}?after=${this.revision}`,
        {
          signal: this.abort.signal,
        },
      );
      if (cached && result.active && result.editable && !this.closed)
        this.restoreLocal(cached);
      await this.load(result);
      this.error = '';
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 0) {
        if (!this.state && cached && !this.closed) {
          this.restoreLocal(cached);
          await this.load({
            active: true,
            editable: true,
            initialText: '',
            maxLength: cached.maxLength,
            revision: cached.revision,
            projectionRevision: cached.projected,
            snapshotRevision: 0,
            snapshot: null,
            updates: [],
          });
        } else if (!this.state) this.failure(cause);
      } else this.failure(cause);
    } finally {
      this.refreshing = false;
      this.publish();
    }
    this.scheduleProjection();
  }

  private async load(state: Snapshot) {
    const previousRevision = this.revision;
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
    if (state.active && (!wasActive || previousRevision !== this.revision))
      this.persistLocal();
  }

  private receive(revision: number, updateId: string, update: Uint8Array) {
    if (revision > this.revision && !this.arrivals.has(revision)) {
      this.apply(update);
      this.arrivals.add(revision);
      this.advance();
    }
    this.pending.delete(updateId);
    this.persistLocal(updateId);
    this.publish();
    if (this.arrivals.size) void this.refresh();
    else this.scheduleProjection();
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

  private restoreLocal(local: LocalDocument) {
    Y.applyUpdate(this.document, local.state, remote);
    this.revision = local.revision;
    this.projected = local.projected;
    for (const [id, bytes] of Object.entries(local.pending))
      this.pending.set(id, bytes);
    this.localSaved = true;
  }

  private persistLocal(acknowledged?: string) {
    if (
      this.accountId === undefined ||
      !this.state?.active ||
      !this.state.editable
    )
      return;
    const accountId = this.accountId;
    const generation = ++this.saveGeneration;
    const doc: LocalDocument = {
      state: Y.encodeStateAsUpdate(this.document),
      maxLength: this.state.maxLength,
      revision: this.revision,
      projected: this.projected,
      pending: Object.fromEntries(this.pending),
    };
    void updateOfflineAccount(accountId, (current) => {
      const previous = current.documents[this.path];
      const pending = Object.fromEntries(
        Object.entries({ ...previous?.pending, ...doc.pending }).filter(
          ([id]) => id !== acknowledged,
        ),
      );
      const merged = previous
        ? {
            ...doc,
            state: Y.mergeUpdates([previous.state, doc.state]),
            revision: Math.max(previous.revision, doc.revision),
            projected: Math.max(previous.projected, doc.projected),
            pending,
          }
        : doc;
      return {
        ...current,
        documents: { ...current.documents, [this.path]: merged },
      };
    })
      .then((current) => {
        if (
          !this.closed &&
          this.saveGeneration === generation &&
          current?.user.id === accountId
        ) {
          this.localSaved = true;
          this.publish();
        }
      })
      .catch(() => undefined);
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

  private needsProjection() {
    return (
      this.state?.editable &&
      this.state.active &&
      this.revision > this.projected &&
      !this.invalid()
    );
  }

  private scheduleProjection() {
    if (this.closed || !this.needsProjection()) return;
    clearTimeout(this.projectTimer);
    this.projectTimer = setTimeout(() => void this.project(), 350);
  }

  private async project() {
    if (
      this.closed ||
      !navigator.onLine ||
      this.projecting ||
      this.refreshing ||
      this.pending.size ||
      this.arrivals.size ||
      !this.needsProjection()
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
      this.persistLocal();
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
      unsaved: this.pending.size > 0 || !!this.needsProjection(),
      localSaved: this.localSaved,
      invalid: !!this.state?.active && this.invalid(),
      error: this.error,
      presence,
    });
  }
}
