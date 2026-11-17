import { http, HttpResponse } from 'msw';
import { server } from './server';

export type ApiRequest = { method: string; body?: string | undefined };
type Reply =
  Response | object | unknown[] | string | number | boolean | null | undefined;

/**
 * Answers every `/api` request through MSW. The handler receives the API path
 * as the client requested it (`/api/projects?x=1`) and returns either a JSON
 * payload (status 200) or an MSW `HttpResponse` for other statuses.
 */
export function mockApi(
  handler: (path: string, request: ApiRequest) => Reply | Promise<Reply>,
) {
  server.use(
    http.all('*/api/*', async ({ request }) => {
      const url = new URL(request.url);
      const body =
        request.method === 'GET' || request.method === 'HEAD'
          ? undefined
          : (await request.text()) || undefined;
      const reply = await handler(url.pathname + url.search, {
        method: request.method,
        body,
      });
      if (reply instanceof Response) return reply;
      return HttpResponse.json(reply === undefined ? {} : reply);
    }),
  );
}

export function apiError(status: number, message: string) {
  return HttpResponse.json({ message }, { status });
}
