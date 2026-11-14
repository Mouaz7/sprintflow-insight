import type { APIRequestContext } from 'playwright';

/** Verification clients explicitly read a resource revision before exercising a mutation. */
export async function versionHeaders(
  request: APIRequestContext,
  base: string,
  path: string,
  method: string,
  data?: unknown,
): Promise<Record<string, string>> {
  if (!['PUT', 'DELETE'].includes(method)) return {};
  const resource =
    /^\/(tasks|sprints|stories|projects)\/(\d+)(?:\/(?:block|unblock|status))?(?:\?.*)?$/.exec(
      path,
    );
  const trash = /^\/workspace\/trash\/(task|sprint|story)\/(\d+)$/.exec(path);
  if (!resource && !trash) return {};
  let version =
    data && typeof data === 'object' && 'version' in data
      ? data.version
      : undefined;
  if (version === undefined) {
    const response = await request.get(
      base +
        '/api' +
        (trash ? '/workspace/trash' : `/${resource![1]}/${resource![2]}`),
    );
    if (response.ok()) {
      const body = await response.json();
      version = trash
        ? body.find(
            (item: { kind: string; id: number }) =>
              item.kind === trash[1] && item.id === Number(trash[2]),
          )?.version
        : body.version;
    }
  }
  return version === undefined ? {} : { 'If-Match': `"${version}"` };
}
