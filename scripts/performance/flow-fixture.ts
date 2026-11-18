// Creates a disposable local account/project and two years of real PostgreSQL status snapshots.
// BASE_URL, FLOW_DB_CONTAINER (or FLOW_PG_URL) and FLOW_FIXTURE_FILE identify the local test environment.
import { execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import {
  existsSync,
  mkdirSync,
  readFileSync,
  writeFileSync,
  unlinkSync,
} from "node:fs";
import { dirname, resolve } from "node:path";

const file = resolve(
  process.env.FLOW_FIXTURE_FILE || "artifacts/flow-performance/fixture.json",
);
const mode = process.argv[2] || "seed";
const base = process.env.BASE_URL || "http://127.0.0.1:18095";
if (!["localhost", "127.0.0.1", "[::1]"].includes(new URL(base).hostname))
  throw new Error("This fixture only targets a local test server.");
type Fixture = {
  source: "b5-flow-load-v1";
  base: string;
  email: string;
  password: string;
  projectId: number;
  cookie: string;
  tasks: number;
  historyRows: number;
  historyDays: number;
};
const cookies = new Map<string, string>();
function cookieHeader() {
  return [...cookies].map(([name, value]) => `${name}=${value}`).join("; ");
}
function loadCookie(cookie: string) {
  for (const item of cookie.split(";")) {
    const at = item.indexOf("=");
    if (at > 0) cookies.set(item.slice(0, at).trim(), item.slice(at + 1));
  }
}
async function api(
  path: string,
  method = "GET",
  data?: unknown,
  version?: number,
): Promise<unknown> {
  const headers: Record<string, string> = { Cookie: cookieHeader() };
  if (method !== "GET") {
    const csrf = (await api("/auth/csrf")) as {
      headerName: string;
      token: string;
    };
    headers.Cookie = cookieHeader();
    headers[csrf.headerName] = csrf.token;
  }
  if (data !== undefined) headers["Content-Type"] = "application/json";
  if (version !== undefined) headers["If-Match"] = `"${version}"`;
  const response = await fetch(base + "/api" + path, {
    method,
    headers,
    redirect: "error",
    ...(data === undefined ? {} : { body: JSON.stringify(data) }),
  });
  for (const value of response.headers.getSetCookie()) {
    const first = value.split(";")[0],
      at = first.indexOf("=");
    if (at > 0) cookies.set(first.slice(0, at), first.slice(at + 1));
  }
  if (!response.ok)
    throw new Error(`${method} ${path}: HTTP ${response.status}`);
  return response.status === 204 ? undefined : response.json();
}
if (mode === "cleanup") {
  if (!existsSync(file)) {
    console.log("No flow fixture to remove.");
    process.exit(0);
  }
  const fixture = JSON.parse(readFileSync(file, "utf8")) as Fixture;
  if (
    fixture.source !== "b5-flow-load-v1" ||
    fixture.base !== base ||
    !/^flow-perf-\d+@example\.com$/.test(fixture.email) ||
    !Number.isSafeInteger(fixture.projectId) ||
    fixture.projectId <= 0
  )
    throw new Error("Unrecognised fixture metadata.");
  loadCookie(fixture.cookie);
  const project = (await api(`/projects/${fixture.projectId}`)) as {
    version: number;
    ownerEmail: string;
  };
  if (project.ownerEmail !== fixture.email)
    throw new Error("The fixture account must own this project.");
  await api(
    `/projects/${fixture.projectId}`,
    "DELETE",
    undefined,
    project.version,
  );
  await api("/auth/account", "DELETE", {
    email: fixture.email,
    currentPassword: fixture.password,
  });
  unlinkSync(file);
  console.log("Removed the disposable flow account and project.");
} else if (mode === "seed") {
  if (existsSync(file))
    throw new Error("Remove the previous fixture before seeding another.");
  const container = process.env.FLOW_DB_CONTAINER;
  const pgUrl = process.env.FLOW_PG_URL;
  if (
    pgUrl &&
    !["localhost", "127.0.0.1", "[::1]"].includes(new URL(pgUrl).hostname)
  )
    throw new Error("FLOW_PG_URL must point to a local test database.");
  if (!container && !pgUrl)
    throw new Error(
      "Set FLOW_DB_CONTAINER or FLOW_PG_URL to the local PostgreSQL test database.",
    );
  const tasks = Number(process.env.FLOW_TASKS || 1000);
  if (!Number.isInteger(tasks) || tasks < 1000 || tasks > 10000)
    throw new Error("FLOW_TASKS must be an integer from 1000 to 10000.");
  const email = `flow-perf-${Date.now()}@example.com`,
    password = `Perf-${randomUUID()}`;
  await api("/auth/register", "POST", {
    email,
    password,
    displayName: "Flow performance demo",
  });
  const project = (await api("/projects", "POST", {
    name: "Two-year flow performance demo",
  })) as { id: number };
  if (!Number.isSafeInteger(project.id) || project.id <= 0)
    throw new Error("Invalid project ID.");
  const q = (value: string) => `'${value.replaceAll("'", "''")}'`;
  const sql = (query: string) =>
    execFileSync(
      pgUrl ? "psql" : process.env.DOCKER_BIN || "docker",
      pgUrl
        ? [pgUrl, "-v", "ON_ERROR_STOP=1", "-At"]
        : [
            "exec",
            "-i",
            container!,
            "sh",
            "-c",
            'exec psql -U "${POSTGRES_USER:-postgres}" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -At',
          ],
      { input: query, encoding: "utf8", maxBuffer: 1024 * 1024 },
    );
  if (
    sql(
      `select count(*) from projects where id=${project.id} and owner_email=${q(email)};`,
    ).trim() !== "1"
  )
    throw new Error("The API project must be present in this test database.");
  sql(`begin;
    create temporary table flow_seed as select n,((n*37)%730)::integer as age_days,
      date_trunc('day',now())-interval '1 second'-((n*37)%730)*interval '1 day' as created
      from generate_series(1,${tasks}) n;
    insert into task(project_id,number,title,status,is_blocked,story_points,created_at,started_at,completed_at,version)
      select ${project.id},n,'Flow demo task '||n,
        case when age_days=0 then 'To Do' when n%10=0 or age_days=1 then 'In Progress' when age_days=2 then 'Review' else 'Done' end,
        false,1,created,case when age_days>=1 then created+interval '24 hours' end,
        case when n%10<>0 and age_days>=3 then created+interval '72 hours' end,0 from flow_seed;
    insert into task_status_history(task_id,project_id,status,changed_at,actor_email,story_points)
      select t.id,${project.id},case when h<24 then 'To Do' when h<48 or s.n%10=0 then 'In Progress' when h<72 then 'Review' else 'Done' end,
        s.created+h*interval '1 hour',${q(email)},1
      from flow_seed s join task t on t.project_id=${project.id} and t.number=s.n cross join generate_series(0,96,2) h
      where s.created+h*interval '1 hour'<now();
    insert into project_task_counter(project_id,last_number) values(${project.id},${tasks})
      on conflict(project_id) do update set last_number=greatest(project_task_counter.last_number,excluded.last_number);
    commit;
    analyze task; analyze task_status_history;`);
  const historyRows = Number(
    sql(
      `select count(*) from task_status_history where project_id=${project.id};`,
    ).trim(),
  );
  const fixture: Fixture = {
    source: "b5-flow-load-v1",
    base,
    email,
    password,
    projectId: project.id,
    cookie: cookieHeader(),
    tasks,
    historyRows,
    historyDays: 730,
  };
  mkdirSync(dirname(file), { recursive: true });
  writeFileSync(file, JSON.stringify(fixture, null, 2) + "\n", {
    mode: 0o600,
    flag: "wx",
  });
  console.log(
    JSON.stringify({
      projectId: project.id,
      tasks,
      historyRows,
      historyDays: 730,
      fixtureFile: file,
    }),
  );
} else throw new Error("Use seed or cleanup.");
