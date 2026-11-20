export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly problem: Readonly<Record<string, unknown>> = {},
  ) {
    super(message);
  }
}

function fallbackErrorMessage(status: number): string {
  if (status === 400)
    return 'Some information is missing or invalid. Check the form and try again.';
  if (status === 401)
    return 'Your session may have expired. Sign in again and retry.';
  if (status === 403)
    return 'You don’t have permission to do that. Ask a project owner or admin for access.';
  if (status === 404)
    return 'We couldn’t find that item. Refresh the page and try again.';
  if (status === 412 || status === 428)
    return 'This item has changed. Reload it and review your unsaved changes before trying again.';
  if (status === 409)
    return 'This change conflicts with newer information. Refresh the page and try again.';
  if (status === 429)
    return 'Too many requests. Wait a moment, then try again.';
  if (status >= 500)
    return 'SprintFlow couldn’t complete that request right now. Please try again in a moment.';
  return 'We couldn’t complete that request. Please try again.';
}

function requestFailed(cause: unknown): never {
  if (cause instanceof ApiError) throw cause;
  throw new ApiError(
    'Could not connect to SprintFlow. Check your internet connection and try again.',
    0,
  );
}

type Csrf = { token: string; headerName: string };
let csrf: Csrf | undefined;

/** A successful login, registration or logout rotates or removes the server session. */
export function clearCsrfToken() {
  csrf = undefined;
}

async function csrfToken(): Promise<Csrf> {
  if (!csrf)
    try {
      const response = await fetch('/api/auth/csrf', {
        credentials: 'same-origin',
      });
      if (!response.ok)
        throw new ApiError(
          fallbackErrorMessage(response.status),
          response.status,
        );
      csrf = (await response.json()) as Csrf;
    } catch (cause) {
      requestFailed(cause);
    }
  return csrf;
}

/** The session's CSRF header, e.g. for the realtime STOMP CONNECT frame. */
export function csrfHeader(): Promise<Csrf> {
  return csrfToken();
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase();
  const headers = new Headers(init.headers);
  if (
    init.body &&
    !(init.body instanceof FormData) &&
    !headers.has('Content-Type')
  )
    headers.set('Content-Type', 'application/json');
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const token = await csrfToken();
    headers.set(token.headerName, token.token);
  }
  let response: Response;
  try {
    response = await fetch(`/api${path}`, {
      ...init,
      headers,
      credentials: 'same-origin',
    });
  } catch (cause) {
    requestFailed(cause);
  }
  if (response.status === 204) return undefined as T;
  const payload: unknown = await response.json().catch(() => ({}));
  if (!response.ok) {
    const problem =
      payload !== null && typeof payload === 'object' && !Array.isArray(payload)
        ? (payload as Record<string, unknown>)
        : {};
    const message =
      typeof problem.message === 'string'
        ? problem.message
        : typeof problem.detail === 'string'
          ? problem.detail
          : '';
    throw new ApiError(
      message || fallbackErrorMessage(response.status),
      response.status,
      problem,
    );
  }
  return payload as T;
}

/** Binary responses share the authenticated, CSRF-protected request boundary. */
export async function apiBlob(path: string, init: RequestInit): Promise<Blob> {
  const headers = new Headers(init.headers);
  const token = await csrfToken();
  headers.set(token.headerName, token.token);
  let response: Response;
  try {
    response = await fetch(`/api${path}`, {
      ...init,
      headers,
      credentials: 'same-origin',
      cache: 'no-store',
    });
  } catch (cause) {
    requestFailed(cause);
  }
  if (!response.ok) {
    const payload: unknown = await response.json().catch(() => ({}));
    const problem =
      payload !== null && typeof payload === 'object' && !Array.isArray(payload)
        ? (payload as Record<string, unknown>)
        : {};
    throw new ApiError(
      typeof problem.detail === 'string'
        ? problem.detail
        : fallbackErrorMessage(response.status),
      response.status,
      problem,
    );
  }
  return response.blob();
}
