import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';
import type { Project } from '../../api/contracts';
import { projectScope } from '../../api/queries';
import { useLanguage, useT } from '../../shared/i18n/LanguageProvider';
import { localizeMessage } from '../../shared/i18n/messages';
import { ErrorState, Skeleton } from '../../shared/ui/States';
import { Notice, useConfirm } from '../../shared/ui/ui';

const eventTypes = [
  'task.created',
  'task.updated',
  'task.moved',
  'task.deleted',
] as const;
type EventType = (typeof eventTypes)[number];
type Webhook = { id: number; url: string; events: EventType[] };
type IssuedWebhook = { webhook: Webhook; secret: string };
type Delivery = {
  id: string;
  event: EventType;
  outcome: 'PENDING' | 'DELIVERED' | 'FAILED';
  attempts: number;
  createdAt: string;
  lastHttpStatus: number | null;
  nextAttemptAt: string | null;
};

const text = {
  EN: {
    title: 'Outgoing webhooks',
    url: 'Webhook HTTPS URL',
    events: 'Events',
    create: 'Create webhook',
    secret: 'New signing secret',
    showOnce: 'Copy this signing secret. It is shown once.',
    hide: 'Hide signing secret',
    empty: 'No outgoing webhooks yet.',
    remove: 'Remove webhook',
    confirm: 'Remove this webhook and its delivery history?',
    'task.created': 'Task created',
    'task.updated': 'Task updated',
    'task.moved': 'Task moved',
    'task.deleted': 'Task deleted',
    history: 'Delivery history',
    none: 'No deliveries yet.',
    load: 'Could not load deliveries.',
    PENDING: 'Pending',
    DELIVERED: 'Delivered',
    FAILED: 'Failed',
    attempts: 'Attempts',
    next: 'Next attempt',
  },
  SV: {
    title: 'Utgående webhooks',
    url: 'Webhookens HTTPS-adress',
    events: 'Händelser',
    create: 'Skapa webhook',
    secret: 'Ny signeringshemlighet',
    showOnce: 'Kopiera signeringshemligheten. Den visas en gång.',
    hide: 'Dölj signeringshemlighet',
    empty: 'Inga utgående webhooks än.',
    remove: 'Ta bort webhook',
    confirm: 'Ta bort webhooken och dess leveranshistorik?',
    'task.created': 'Uppgift skapad',
    'task.updated': 'Uppgift uppdaterad',
    'task.moved': 'Uppgift flyttad',
    'task.deleted': 'Uppgift borttagen',
    history: 'Leveranshistorik',
    none: 'Inga leveranser än.',
    load: 'Det gick inte att ladda leveranserna.',
    PENDING: 'Väntar',
    DELIVERED: 'Levererad',
    FAILED: 'Misslyckad',
    attempts: 'Försök',
    next: 'Nästa försök',
  },
} as const;

const path = (project: number) => `/projects/${project}/webhooks`;
const scope = (project: number) => [
  ...projectScope(project),
  'outbound-webhooks',
];

export default function OutboundWebhookPanel({
  project,
}: {
  project: Project;
}) {
  const t = useT(text);
  const { language } = useLanguage();
  const client = useQueryClient();
  const confirm = useConfirm();
  const [expanded, setExpanded] = useState(false);
  const [events, setEvents] = useState<EventType[]>(['task.created']);
  const [issued, setIssued] = useState<IssuedWebhook>();
  const queryKey = scope(project.id);
  const list = useQuery({
    queryKey,
    queryFn: () => api<Webhook[]>(path(project.id)),
    enabled: expanded,
  });
  const refresh = () => client.invalidateQueries({ queryKey });
  const create = useMutation({
    mutationFn: (url: string) =>
      api<IssuedWebhook>(path(project.id), {
        method: 'POST',
        body: JSON.stringify({ url, events }),
      }),
    onSuccess: (result) => {
      setIssued(result);
      return refresh();
    },
    gcTime: 0,
  });
  const remove = useMutation({
    mutationFn: (id: number) =>
      api(`${path(project.id)}/${id}`, { method: 'DELETE' }),
    onSuccess: (_, id) => {
      if (issued?.webhook.id === id) {
        setIssued(undefined);
        create.reset();
      }
      return refresh();
    },
  });
  const error = create.error ?? remove.error ?? list.error;
  return (
    <details
      className="flow-panel chart-table"
      onToggle={(event) => setExpanded(event.currentTarget.open)}
    >
      <summary>{t('title')}</summary>
      {expanded && list.isPending && <Skeleton />}
      {error && <Notice error={localizeMessage(error.message, language)} />}
      {issued && (
        <div>
          <p role="status">{t('showOnce')}</p>
          <label>
            {t('secret')}{' '}
            <input readOnly value={issued.secret} autoComplete="off" />
          </label>
          <button
            type="button"
            onClick={() => {
              setIssued(undefined);
              create.reset();
            }}
          >
            {t('hide')}
          </button>
        </div>
      )}
      {!project.archivedAt && !issued && (
        <form
          className="editor-form"
          onSubmit={(event) => {
            event.preventDefault();
            const values = new FormData(event.currentTarget);
            if (events.length) create.mutate(String(values.get('url') ?? ''));
          }}
        >
          <label>
            {t('url')}{' '}
            <input
              name="url"
              type="url"
              required
              maxLength={1024}
              placeholder="https://"
            />
          </label>
          <fieldset disabled={create.isPending}>
            <legend>{t('events')}</legend>
            {eventTypes.map((type) => (
              <label key={type}>
                <input
                  type="checkbox"
                  checked={events.includes(type)}
                  onChange={(event) => {
                    const checked = event.target.checked;
                    setEvents((previous) =>
                      checked
                        ? [...previous, type]
                        : previous.filter((item) => item !== type),
                    );
                  }}
                />{' '}
                {t(type)}
              </label>
            ))}
          </fieldset>
          <button
            className="primary-action"
            disabled={create.isPending || !events.length}
          >
            {t('create')}
          </button>
        </form>
      )}
      {list.data?.length === 0 && <p>{t('empty')}</p>}
      <ul>
        {list.data?.map((hook) => (
          <li key={hook.id}>
            <strong>{hook.url}</strong>
            <p>{hook.events.map((type) => t(type)).join(', ')}</p>
            <WebhookHistory projectId={project.id} webhookId={hook.id} />
            {!project.archivedAt && (
              <button
                type="button"
                className="danger-action"
                disabled={remove.isPending}
                onClick={async () => {
                  if (await confirm(t('confirm'))) remove.mutate(hook.id);
                }}
              >
                {t('remove')}
              </button>
            )}
          </li>
        ))}
      </ul>
    </details>
  );
}

function WebhookHistory({
  projectId,
  webhookId,
}: {
  projectId: number;
  webhookId: number;
}) {
  const t = useT(text);
  const { format } = useLanguage();
  const [open, setOpen] = useState(false);
  const history = useQuery({
    queryKey: [...scope(projectId), webhookId, 'deliveries'],
    queryFn: () =>
      api<Delivery[]>(`${path(projectId)}/${webhookId}/deliveries`),
    enabled: open,
    refetchInterval: 15_000,
  });
  return (
    <details onToggle={(event) => setOpen(event.currentTarget.open)}>
      <summary>{t('history')}</summary>
      {open && history.isPending && <Skeleton />}
      {history.isError && (
        <ErrorState detail={t('load')} onRetry={() => void history.refetch()} />
      )}
      {history.data?.length === 0 && <p>{t('none')}</p>}
      {history.data && (
        <ul>
          {history.data.map((item) => (
            <li key={item.id}>
              {t(item.event)} · {t(item.outcome)} · {t('attempts')}{' '}
              {item.attempts}
              {item.lastHttpStatus !== null && ` · HTTP ${item.lastHttpStatus}`}
              {' · '}
              <time dateTime={item.createdAt}>
                {format.dateTime(item.createdAt)}
              </time>
              {item.nextAttemptAt &&
                ` · ${t('next')} ${format.dateTime(item.nextAttemptAt)}`}
            </li>
          ))}
        </ul>
      )}
    </details>
  );
}
