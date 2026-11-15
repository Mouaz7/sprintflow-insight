// k6 load script (runs in k6, not Node): measures authenticated workspace reads.
// Usage: BASE_URL=http://127.0.0.1:18084 k6 run scripts/performance/workspace-read.k6.js
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    warmup: { executor: 'shared-iterations', vus: 1, iterations: 10, exec: 'readWorkspace' },
    measure: {
      executor: 'constant-vus',
      vus: 5,
      duration: '20s',
      startTime: '3s',
      exec: 'readWorkspace',
    },
  },
  thresholds: {
    'http_req_failed{scenario:measure}': ['rate<0.01'],
    checks: ['rate==1'],
  },
};

const base = __ENV.BASE_URL || 'http://127.0.0.1:18084';

function routeName(path) {
  return path.replace(/\d+/g, ':id');
}

function api(path, method = 'GET', data, session) {
  const headers = { 'Content-Type': 'application/json' };
  if (session) headers.Cookie = session;
  if (method !== 'GET') {
    const csrf = http.get(`${base}/api/auth/csrf`, { headers });
    headers[csrf.json('headerName')] = csrf.json('token');
  }
  const body = data === undefined ? null : JSON.stringify(data);
  const response = http.request(method, `${base}/api${path}`, body, {
    headers,
    tags: { name: routeName(path) },
  });
  const ok = check(response, {
    [`setup ${method} ${path}`]: (r) => r.status >= 200 && r.status < 300,
  });
  if (!ok) throw new Error(`Fixture failed ${response.status}`);
  return response;
}

export function setup() {
  const stamp = Date.now();
  const password = `Perf-only-${stamp}-Secret`;
  const email = `a4-perf-${stamp}@example.com`;
  const registered = api('/auth/register', 'POST', {
    email,
    password,
    displayName: 'A4 performance fixture',
  });
  const session = `SPRINTFLOW_SESSION=${registered.cookies.SPRINTFLOW_SESSION[0].value}`;
  const project = api(
    '/projects/templates',
    'POST',
    {
      name: `Performance fixture ${stamp}`,
      description: 'Fixed 23-task Scrum dataset',
      template: 'Scrum',
      language: 'EN',
    },
    session,
  ).json();
  for (let i = 0; i < 20; i++) {
    api(
      '/tasks',
      'POST',
      {
        projectId: project.id,
        title: `Performance task ${i}`,
        description: 'Fixed reproducible workload',
        status: 'To Do',
        storyPoints: 1,
      },
      session,
    );
  }
  return { session, projectId: project.id, email, password };
}

export function readWorkspace(data) {
  const headers = { Cookie: data.session };
  const paths = ['/projects', `/tasks?projectId=${data.projectId}`, `/projects/${data.projectId}/metrics`];
  for (const path of paths) {
    const response = http.get(`${base}/api${path}`, { headers, tags: { name: routeName(path) } });
    check(response, { 'authenticated workspace read succeeds': (r) => r.status === 200 });
  }
  sleep(0.1);
}

export function teardown(data) {
  api(`/projects/${data.projectId}`, 'DELETE', undefined, data.session);
  api('/auth/account', 'DELETE', { email: data.email, currentPassword: data.password }, data.session);
}
