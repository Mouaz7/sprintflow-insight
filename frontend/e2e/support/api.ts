import {
  expect,
  type APIRequestContext,
  type APIResponse,
} from '@playwright/test';
import { setTimeout } from 'node:timers/promises';

interface Csrf {
  headerName: string;
  token: string;
}

type Method = 'GET' | 'POST' | 'PUT' | 'DELETE';

/**
 * Thin client for the real REST API. It shares cookies with the browser context
 * it was created from, so a page and its API calls act as the same account.
 */
export class Api {
  constructor(private readonly request: APIRequestContext) {}

  async call(
    path: string,
    method: Method = 'GET',
    data?: unknown,
  ): Promise<APIResponse> {
    const headers: Record<string, string> = {};
    if (method !== 'GET') {
      const csrf = (await (
        await this.request.get('/api/auth/csrf')
      ).json()) as Csrf;
      headers[csrf.headerName] = csrf.token;
      Object.assign(headers, await this.versionHeaders(path, method, data));
    }
    return this.request.fetch('/api' + path, {
      method,
      headers,
      ...(data === undefined ? {} : { data }),
    });
  }

  async json<T>(
    path: string,
    method: Method = 'GET',
    data?: unknown,
  ): Promise<T> {
    const response = await this.call(path, method, data);
    expect(
      response.ok(),
      `${method} ${path}: ${response.status()} ${await response.text()}`,
    ).toBe(true);
    const body = await response.text();
    return (body ? JSON.parse(body) : undefined) as T;
  }

  async status(
    path: string,
    method: Method = 'GET',
    data?: unknown,
  ): Promise<number> {
    return (await this.call(path, method, data)).status();
  }

  /** Mutations use optimistic locking, so read the current revision first. */
  private async versionHeaders(
    path: string,
    method: Method,
    data?: unknown,
  ): Promise<Record<string, string>> {
    if (method !== 'PUT' && method !== 'DELETE') return {};
    const assignment = /^\/projects\/\d+\/stories\/(\d+)\/epic$/.exec(path);
    const resource =
      /^\/(tasks|sprints|stories|projects)\/(\d+)(?:\/(?:block|unblock|status))?(?:\?.*)?$/.exec(
        assignment ? `/stories/${assignment[1]}` : path,
      );
    const trash = /^\/workspace\/trash\/(task|sprint|story)\/(\d+)$/.exec(path);
    if (!resource && !trash) return {};
    let version: unknown =
      data && typeof data === 'object' && 'version' in data
        ? data.version
        : undefined;
    if (version === undefined) {
      const response = await this.request.get(
        '/api' +
          (trash ? '/workspace/trash' : `/${resource?.[1]}/${resource?.[2]}`),
      );
      if (response.ok()) {
        const body = (await response.json()) as
          | { version?: number }
          | { kind: string; id: number; version: number }[];
        version = Array.isArray(body)
          ? body.find(
              (item) =>
                item.kind === trash?.[1] && item.id === Number(trash?.[2]),
            )?.version
          : body.version;
      }
    }
    return version === undefined ? {} : { 'If-Match': `"${String(version)}"` };
  }
}

export interface Account {
  email: string;
  password: string;
  displayName: string;
}

let sequence = 0;

/** Unique, disposable credentials. Every test removes the accounts it creates. */
export function newAccount(label: string): Account {
  sequence += 1;
  const stamp = `${Date.now()}-${process.pid}-${sequence}`;
  return {
    email: `e2e-${label}-${stamp}@example.com`,
    password: `E2e-${crypto.randomUUID()}`,
    displayName: `E2E ${label}`,
  };
}

export async function register(api: Api, account: Account): Promise<void> {
  let response = await api.call('/auth/register', 'POST', account);
  if (response.status() === 429) {
    // All disposable users share the runner's client address. Keep the app's
    // real 20/minute registration protection and respect its refill interval.
    const seconds = Number(response.headers()['retry-after']);
    expect(Number.isInteger(seconds) && seconds >= 1 && seconds <= 60).toBe(
      true,
    );
    expect((await response.json()).code).toBe('RATE_LIMITED');
    await setTimeout(seconds * 1000);
    response = await api.call('/auth/register', 'POST', account);
  }
  expect(
    response.ok(),
    `Registration: ${response.status()} ${await response.text()}`,
  ).toBe(true);
}

/** Deletes every project the account owns, then the account itself. */
export async function removeAccount(api: Api, account: Account): Promise<void> {
  const projects =
    await api.json<{ id: number; ownerEmail: string }[]>('/projects');
  for (const project of projects.filter(
    (item) => item.ownerEmail === account.email,
  )) {
    await api.json(`/projects/${project.id}`, 'DELETE');
  }
  await api.json('/auth/account', 'DELETE', {
    email: account.email,
    currentPassword: account.password,
  });
}
