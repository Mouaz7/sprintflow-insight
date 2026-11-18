import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

export type HistoricalTask = {
  id: number;
  sprintId: number;
  createdAt: string;
  completedAt: string;
};
export type HistoricalSprint = {
  id: number;
  createdAt: string;
  completedAt: string;
};

/** Backdate only this disposable API-created project's history; no HTTP response is mocked. */
export function seedForecastHistory(
  baseURL: string,
  projectId: number,
  owner: string,
  tasks: HistoricalTask[],
  sprints: HistoricalSprint[],
) {
  if (!['localhost', '127.0.0.1', '[::1]'].includes(new URL(baseURL).hostname))
    throw new Error(
      'Historical forecast fixtures require a local test application.',
    );
  const id = (value: number) => {
    if (!Number.isSafeInteger(value) || value < 1)
      throw new Error('Invalid fixture id.');
    return value;
  };
  const quote = (value: string) => `'${value.replaceAll("'", "''")}'`;
  const stamp = (value: string) => {
    if (!Number.isFinite(Date.parse(value)))
      throw new Error('Invalid fixture timestamp.');
    return quote(new Date(value).toISOString());
  };
  const docker = process.env.DOCKER_BIN || 'docker';
  const container =
    process.env.E2E_DB_CONTAINER ||
    execFileSync(
      docker,
      [
        'compose',
        '-f',
        fileURLToPath(new URL('../../../compose.yaml', import.meta.url)),
        'ps',
        '-q',
        'postgres',
      ],
      { encoding: 'utf8' },
    ).trim();
  if (!container)
    throw new Error(
      'Set E2E_DB_CONTAINER to the PostgreSQL container used by this local app.',
    );
  const sql = (query: string) =>
    execFileSync(
      docker,
      [
        'exec',
        '-i',
        container,
        'sh',
        '-c',
        'exec psql -U "${POSTGRES_USER:-postgres}" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -At',
      ],
      { input: query, encoding: 'utf8' },
    );
  if (
    !/^e2e-.*@example\.com$/.test(owner) ||
    sql(
      `select count(*) from projects where id=${id(projectId)} and owner_email=${quote(owner)};`,
    ).trim() !== '1'
  )
    throw new Error(
      'The disposable API project and owner must match this test database.',
    );
  const updates = tasks.map(
    (
      task,
    ) => `update task set created_at=${stamp(task.createdAt)}, completed_at=${stamp(task.completedAt)}, sprint_id=${id(task.sprintId)} where id=${id(task.id)} and project_id=${projectId};
    update task_status_history set changed_at=case when status='Done' then ${stamp(task.completedAt)}::timestamptz else ${stamp(task.createdAt)}::timestamptz end, sprint_id=${task.sprintId} where task_id=${task.id} and project_id=${projectId};`,
  );
  updates.push(
    ...sprints.map(
      (sprint) =>
        `update sprint set created_at=${stamp(sprint.createdAt)},completed_at=${stamp(sprint.completedAt)} where id=${id(sprint.id)} and project_id=${projectId};`,
    ),
  );
  sql('begin;\n' + updates.join('\n') + '\ncommit;');
}
