import { createServer } from 'node:http';
import { createReadStream, existsSync, statSync } from 'node:fs';
import { resolve, extname, sep } from 'node:path';
import { fileURLToPath, URL } from 'node:url';
import { log } from 'node:console';
const directory = fileURLToPath(
  new URL('../../storybook-static/', import.meta.url),
);
const types: Record<string, string> = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.woff2': 'font/woff2',
  '.ico': 'image/x-icon',
};
createServer((request, response) => {
  const pathname = decodeURIComponent(
    new URL(request.url ?? '/', 'http://localhost').pathname,
  );
  const path = resolve(
    directory,
    '.' + pathname + (pathname.endsWith('/') ? 'index.html' : ''),
  );
  if (
    !path.startsWith(resolve(directory) + sep) ||
    !existsSync(path) ||
    !statSync(path).isFile()
  ) {
    response.writeHead(404).end();
    return;
  }
  response.writeHead(200, {
    'Content-Type': types[extname(path)] ?? 'application/octet-stream',
    'Cache-Control': 'no-store',
  });
  createReadStream(path).pipe(response);
}).listen(6006, '127.0.0.1', () =>
  log('Storybook fixture server http://127.0.0.1:6006'),
);
