// Captures the README screenshots from a running SprintFlow app.
//
//   BASE_URL=http://127.0.0.1:8081 npm run screenshots --prefix scripts
//
// The script registers three disposable demo accounts (example.com addresses),
// seeds a demo project through the public REST API, captures the main pages and
// writes WebP images to docs/images/screenshots/. Run it against an empty,
// throwaway database: the demo addresses and project name are fixed, so a second
// run on the same database fails with 409. The data is left in place.
//
// Environment:
//   BASE_URL (or SPRINTFLOW_QA_URL)  app address, default http://127.0.0.1:8081
//   SCREENSHOT_DIR                   output directory, default docs/images/screenshots
//   SCREENSHOT_QUALITY               WebP quality 0..1, default 0.82
//   SCREENSHOT_DB_CONTAINER          optional disposable PostgreSQL container.
//                                    When set, Docker spreads the seeded status history
//                                    over the past weeks so burndown and cycle time are
//                                    meaningful. The REST API stamps every transition
//                                    with the server clock, so it cannot do this itself.
//   SCREENSHOT_DB_NAME               database inside that container, default sprintflow
//   PLAYWRIGHT_EXECUTABLE_PATH       optional existing Chromium
import { execFileSync } from "node:child_process";
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { createHash } from "node:crypto";
import { fileURLToPath } from "node:url";
import { createRequire } from "node:module";
import type {
  APIRequestContext,
  Browser,
  BrowserContext,
  Page,
} from "playwright";
import { launchBrowser } from "./browser.ts";

const require = createRequire(import.meta.url);
const { request: playwrightRequest }: typeof import("playwright") = require(
  process.env.PLAYWRIGHT_MODULE || "playwright",
);

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const baseURL = (
  process.env.BASE_URL ??
  process.env.SPRINTFLOW_QA_URL ??
  "http://127.0.0.1:8081"
).replace(/\/$/, "");
const outDir = resolve(
  root,
  process.env.SCREENSHOT_DIR ?? "docs/images/screenshots",
);
const quality = Number(process.env.SCREENSHOT_QUALITY ?? "0.82");
if (!Number.isFinite(quality) || quality < 0 || quality > 1)
  throw new Error("SCREENSHOT_QUALITY must be 0..1.");
const target = new URL(baseURL);
if (
  !["http:", "https:"].includes(target.protocol) ||
  target.username ||
  target.password
)
  throw new Error("Use an HTTP(S) app URL without credentials.");
const desktop = { width: 1440, height: 900 };
const mobile = { width: 390, height: 844 };

type Method = "GET" | "POST" | "PUT" | "DELETE";
type Json = Record<string, unknown>;

/** Minimal REST client: CSRF on writes and If-Match for versioned resources. */
class Api {
  readonly request: APIRequestContext;

  constructor(request: APIRequestContext) {
    this.request = request;
  }

  async call<T = Json>(
    path: string,
    method: Method = "GET",
    data?: unknown,
    version?: number,
  ): Promise<T> {
    const headers: Record<string, string> = {};
    if (method !== "GET") {
      const csrf = (await (
        await this.request.get("/api/auth/csrf")
      ).json()) as { headerName: string; token: string };
      headers[csrf.headerName] = csrf.token;
    }
    if (version !== undefined) headers["If-Match"] = `"${version}"`;
    const response = await this.request.fetch("/api" + path, {
      method,
      headers,
      ...(data === undefined ? {} : { data }),
    });
    const body = await response.text();
    if (!response.ok())
      throw new Error(`${method} ${path}: ${response.status()} ${body}`);
    return (body ? JSON.parse(body) : undefined) as T;
  }
}

interface Account {
  email: string;
  password: string;
  displayName: string;
}

function account(role: string, displayName: string): Account {
  return {
    email: `demo.${role}@example.com`,
    password: `Demo-${crypto.randomUUID()}`,
    displayName,
  };
}

function day(offset: number): string {
  return new Date(Date.now() + offset * 86_400_000).toISOString().slice(0, 10);
}

function dueAt(offset: number): string {
  return `${day(offset)}T15:00:00Z`;
}

async function signedIn(person: Account, register: boolean): Promise<Api> {
  const api = new Api(await playwrightRequest.newContext({ baseURL }));
  if (register) await api.call("/auth/register", "POST", person);
  else
    await api.call("/auth/login", "POST", {
      email: person.email,
      password: person.password,
    });
  await api.call("/auth/preferences", "PUT", {
    language: "EN",
    theme: "light",
    onboardingCompleted: true,
  });
  return api;
}

interface Task {
  id: number;
  version: number;
  title: string;
}

interface TaskSpec {
  title: string;
  description?: string;
  story: number;
  status: "To Do" | "In Progress" | "Review" | "Done";
  assignee?: Account;
  points: number;
  labels?: string[];
  due?: number;
  checklist?: [string, boolean][];
  blocked?: string;
}

const flow = ["To Do", "In Progress", "Review", "Done"] as const;

interface Seeded {
  owner: Account;
  developer: Account;
  projectId: number;
  reportPath: string;
  commentedTask: string;
  pokerId: number;
  sprintId: number;
}

async function seed(): Promise<Seeded> {
  const owner = account("owner", "Demo Owner");
  const developer = account("developer", "Demo Developer");
  const designer = account("designer", "Demo Designer");
  const api = await signedIn(owner, true);
  const dev = await signedIn(developer, true);
  const design = await signedIn(designer, true);

  const project = await api.call<{ id: number }>("/projects", "POST", {
    name: "Payments Platform",
    description: "Checkout, card payments and refunds for the web shop",
    key: "PAY",
  });
  for (const member of [developer, designer]) {
    await api.call(`/projects/${project.id}/members`, "POST", {
      email: member.email,
      role: "Member",
    });
  }

  const sprint = (
    name: string,
    goal: string,
    status: string,
    start: number,
    end: number,
    totalStoryPoints: number,
  ) =>
    api.call<{ id: number; version: number }>("/sprints", "POST", {
      name,
      goal,
      status,
      totalStoryPoints,
      projectId: project.id,
      startDate: day(start),
      endDate: day(end),
    });
  const sprint1 = await sprint(
    "Sprint 1",
    "Customers can check out as guests",
    "Active",
    -24,
    -11,
    13,
  );

  const story = (
    title: string,
    points: number,
    priorityId: number,
    businessValue: number,
    sprintId: number | null,
    acceptanceCriteria: string,
  ) =>
    api.call<{ id: number }>("/stories", "POST", {
      title,
      description: "",
      acceptanceCriteria,
      storyPoints: points,
      priorityId,
      businessValue,
      projectId: project.id,
      sprintId,
    });

  const createTask = async (
    spec: TaskSpec,
    sprintId: number | null,
  ): Promise<Task> => {
    const body = (status: string, extra: Json = {}) => ({
      title: spec.title,
      description: spec.description ?? "",
      status,
      blocked: false,
      blockReason: null,
      projectId: project.id,
      sprintId,
      storyId: spec.story,
      assigneeEmail: spec.assignee?.email ?? null,
      storyPoints: spec.points,
      dueAt: spec.due === undefined ? null : dueAt(spec.due),
      labels: spec.labels ?? [],
      checklist: (spec.checklist ?? []).map(([label, completed], index) => ({
        id: `step-${index + 1}`,
        label,
        completed,
        required: index === 0,
      })),
      ...extra,
    });
    let task = await api.call<Task>("/tasks", "POST", body("To Do"));
    // Walk the workflow so status history (cycle time, burndown) is recorded.
    for (const status of flow.slice(1, flow.indexOf(spec.status) + 1)) {
      task = await api.call<Task>(
        `/tasks/${task.id}`,
        "PUT",
        body(status),
        task.version,
      );
    }
    if (spec.blocked) {
      task = await api.call<Task>(
        `/tasks/${task.id}/block?reason=${encodeURIComponent(spec.blocked)}`,
        "PUT",
        undefined,
        task.version,
      );
    }
    return task;
  };

  // Sprint 1: delivered and closed, so history and the completed-sprint chart have data.
  const guest = await story(
    "Guest checkout with saved address",
    5,
    1,
    5,
    sprint1.id,
    "A guest can pay without creating an account",
  );
  const sandbox = await story(
    "Payment provider sandbox integration",
    8,
    1,
    4,
    sprint1.id,
    "Test payments succeed end to end in the sandbox",
  );
  const doneTasks: TaskSpec[] = [
    {
      title: "Address form with validation",
      story: guest.id,
      status: "Done",
      assignee: designer,
      points: 2,
      labels: ["frontend", "ux"],
    },
    {
      title: "Persist guest orders",
      story: guest.id,
      status: "Done",
      assignee: developer,
      points: 3,
      labels: ["backend"],
    },
    {
      title: "Sandbox API client",
      story: sandbox.id,
      status: "Done",
      assignee: developer,
      points: 5,
      labels: ["backend", "api"],
    },
    {
      title: "Webhook signature check",
      story: sandbox.id,
      status: "Done",
      assignee: owner,
      points: 3,
      labels: ["security"],
    },
  ];
  for (const spec of doneTasks) await createTask(spec, sprint1.id);
  const closed = await api.call<{ version: number }>(`/sprints/${sprint1.id}`);
  await api.call(
    `/sprints/${sprint1.id}/status?status=Completed`,
    "PUT",
    undefined,
    closed.version,
  );

  const sprint2 = await sprint(
    "Sprint 2",
    "Card payments with 3-D Secure in production",
    "Active",
    -10,
    3,
    16,
  );
  const sprint3 = await sprint(
    "Sprint 3",
    "Refunds and payment exports",
    "Planned",
    4,
    17,
    11,
  );

  const card = await story(
    "Pay by card with 3-D Secure",
    8,
    1,
    5,
    sprint2.id,
    "Card payments pass the issuer challenge",
  );
  const email = await story(
    "Order confirmation email",
    3,
    2,
    3,
    sprint2.id,
    "The customer receives a receipt within a minute",
  );
  const retry = await story(
    "Retry failed payments automatically",
    5,
    2,
    4,
    sprint2.id,
    "Soft declines are retried up to three times",
  );
  await story(
    "Partial refunds from the admin console",
    8,
    1,
    4,
    sprint3.id,
    "Support staff can refund part of an order",
  );
  await story(
    "Export transactions to CSV",
    3,
    3,
    2,
    sprint3.id,
    "Finance can download a month of transactions",
  );
  const pokerStory = await story(
    "Fraud score on high-value orders",
    8,
    2,
    4,
    null,
    "Orders above the threshold show a risk score",
  );
  await story(
    "Apple Pay and Google Pay",
    5,
    3,
    3,
    null,
    "Wallet payments work on supported devices",
  );

  const commentedTask = "3-D Secure challenge screen";
  const sprintTasks: TaskSpec[] = [
    {
      title: "Card tokenisation endpoint",
      story: card.id,
      status: "Done",
      assignee: developer,
      points: 3,
      labels: ["backend", "api"],
    },
    {
      title: "Receipt email template",
      story: email.id,
      status: "Done",
      assignee: designer,
      points: 1,
      labels: ["ux"],
    },
    {
      title: commentedTask,
      story: card.id,
      status: "Review",
      assignee: designer,
      points: 3,
      labels: ["frontend"],
      due: 1,
      checklist: [
        ["Accessible error messages", true],
        ["Mobile layout", true],
        ["Copy review", false],
      ],
    },
    {
      title: "Send receipt after capture",
      story: email.id,
      status: "Review",
      assignee: developer,
      points: 2,
      labels: ["backend"],
    },
    {
      title: "Issuer challenge callback",
      story: card.id,
      status: "In Progress",
      assignee: developer,
      points: 5,
      labels: ["backend", "security"],
      due: 2,
    },
    {
      title: "Retry schedule for soft declines",
      story: retry.id,
      status: "In Progress",
      assignee: owner,
      points: 3,
      labels: ["backend"],
      blocked: "Waiting for provider rate limits",
    },
    {
      title: "Payment status banner",
      story: card.id,
      status: "In Progress",
      assignee: designer,
      points: 2,
      labels: ["frontend", "ux"],
    },
    {
      title: "Decline reason mapping",
      story: retry.id,
      status: "To Do",
      assignee: developer,
      points: 2,
      labels: ["backend"],
      due: 3,
    },
    {
      title: "Retry dashboard widget",
      story: retry.id,
      status: "To Do",
      points: 2,
      labels: ["frontend"],
    },
    {
      title: "Load test checkout",
      story: card.id,
      status: "To Do",
      assignee: owner,
      points: 3,
      labels: ["api"],
    },
  ];
  const created: Task[] = [];
  for (const spec of sprintTasks)
    created.push(await createTask(spec, sprint2.id));

  const challenge = created.find((task) => task.title === commentedTask);
  if (challenge) {
    await dev.call(`/tasks/${challenge.id}/comments`, "POST", {
      message:
        "@demo.owner@example.com The challenge iframe now resizes on small screens. Ready for review.",
    });
    await api.call(`/tasks/${challenge.id}/comments`, "POST", {
      message:
        "Looks good. Please check the copy with the Swedish translation before merging.",
    });
    await design.call(`/tasks/${challenge.id}/comments`, "POST", {
      message: "Copy updated in both languages.",
    });
  }

  for (const [category, message] of [
    ["well", "Sandbox integration was finished two days early."],
    ["well", "Pairing on the webhook check caught a signature bug."],
    ["improve", "Stories entered the sprint without acceptance criteria."],
    ["actions", "Refine the next sprint backlog together on Monday."],
  ]) {
    await api.call(`/projects/${project.id}/retrospective`, "POST", {
      sprintId: sprint1.id,
      category,
      message,
    });
  }

  const epic = await api.call<{ id: number }>(
    `/projects/${project.id}/epics`,
    "POST",
    {
      title: "Reliable checkout",
      description: "Guest checkout and card payments",
      color: "#4f46e5",
      startDate: day(-24),
      targetDate: day(17),
    },
  );
  for (const item of [guest, sandbox, card, email, retry]) {
    const current = await api.call<{ version: number }>(`/stories/${item.id}`);
    await api.call(
      `/projects/${project.id}/stories/${item.id}/epic`,
      "PUT",
      { epicId: epic.id },
      current.version,
    );
  }
  for (const [name, kind, options] of [
    ["Release", "SELECT", ["Pilot", "General availability"]],
    ["Customer value", "NUMBER", []],
  ] as const)
    await api.call(`/projects/${project.id}/custom-fields`, "POST", {
      name,
      kind,
      options,
    });
  const firstTask = created[0];
  await api.call(
    `/projects/${project.id}/tasks/${firstTask.id}/time-estimate`,
    "PUT",
    { estimateMinutes: 180 },
    firstTask.version,
  );
  await dev.call(`/projects/${project.id}/time-entries`, "POST", {
    taskId: firstTask.id,
    minutes: 120,
    spentOn: day(-1),
    note: "Tokenisation and API tests",
  });
  const poker = await api.call<{ id: number }>(
    `/projects/${project.id}/poker-sessions`,
    "POST",
    { storyId: pokerStory.id, deck: "FIBONACCI" },
  );
  await api.call(
    `/projects/${project.id}/poker-sessions/${poker.id}/vote`,
    "PUT",
    { round: 1, card: "5" },
  );
  await dev.call(
    `/projects/${project.id}/poker-sessions/${poker.id}/vote`,
    "PUT",
    { round: 1, card: "8" },
  );
  await api.call(
    `/projects/${project.id}/poker-sessions/${poker.id}/reveal`,
    "POST",
    { round: 1 },
  );

  if (process.env.SCREENSHOT_DB_CONTAINER)
    backdateHistory(
      process.env.SCREENSHOT_DB_CONTAINER,
      project.id,
      sprint1.id,
    );
  // The shared report is a snapshot, so it is created after any backdating.
  const report = await api.call<{ path: string }>(
    `/projects/${project.id}/reports`,
    "POST",
    { sprintId: sprint2.id },
  );
  await Promise.all(
    [api, dev, design].map((client) => client.request.dispose()),
  );
  return {
    owner,
    developer,
    projectId: project.id,
    reportPath: report.path,
    commentedTask,
    pokerId: poker.id,
    sprintId: sprint2.id,
  };
}

/**
 * Spreads the seeded workflow history over the past weeks: Sprint 1 work moves
 * through the board between 24 and 16 days ago and the sprint closes 11 days ago;
 * Sprint 2 work starts 10 days ago. Task timestamps follow their history.
 */
function backdateHistory(
  container: string,
  projectId: number,
  sprint1Id: number,
): void {
  const p = Number(projectId);
  const s1 = Number(sprint1Id);
  if (!Number.isSafeInteger(p) || !Number.isSafeInteger(s1))
    throw new Error("Invalid demo identifiers.");
  const sql = `
    BEGIN;
    DO $$ BEGIN
      IF NOT EXISTS (SELECT 1 FROM projects WHERE id = ${p} AND owner_email = 'demo.owner@example.com' AND key = 'PAY')
      THEN RAISE EXCEPTION 'Refusing to alter a non-demo project'; END IF;
    END $$;
    WITH t AS (SELECT id, sprint_id, row_number() OVER (ORDER BY id) AS n FROM task WHERE project_id = ${p}),
         h AS (SELECT id, task_id, row_number() OVER (PARTITION BY task_id ORDER BY changed_at, id) AS r
                 FROM task_status_history WHERE project_id = ${p}),
         plan AS (SELECT h.id, CASE WHEN t.sprint_id = ${s1}
                    THEN now() - interval '24 days' + t.n * interval '12 hours' + (h.r - 1) * interval '2 days'
                    ELSE now() - interval '10 days' + (t.n % 5) * interval '12 hours' + (h.r - 1) * interval '36 hours'
                  END AS at FROM h JOIN t ON t.id = h.task_id)
    UPDATE task_status_history x SET changed_at = plan.at FROM plan WHERE x.id = plan.id;
    UPDATE task x SET created_at = a.first,
                      started_at = COALESCE(a.started, x.started_at),
                      completed_at = COALESCE(a.done, x.completed_at)
      FROM (SELECT task_id, min(changed_at) AS first,
                   min(changed_at) FILTER (WHERE status = 'In Progress') AS started,
                   max(changed_at) FILTER (WHERE status = 'Done') AS done
              FROM task_status_history WHERE project_id = ${p} GROUP BY task_id) a
     WHERE x.id = a.task_id;
    UPDATE sprint SET created_at = now() - interval '25 days', completed_at = now() - interval '11 days' WHERE id = ${s1} AND project_id = ${p};
    COMMIT;`;
  execFileSync(
    "docker",
    [
      "exec",
      "-i",
      container,
      "psql",
      "-U",
      "sprintflow",
      "-d",
      process.env.SCREENSHOT_DB_NAME ?? "sprintflow",
      "-q",
      "-v",
      "ON_ERROR_STOP=1",
    ],
    { input: sql, stdio: ["pipe", "inherit", "inherit"] },
  );
  console.log("  backdated workflow history");
}

/** Re-encodes a PNG screenshot as WebP with the browser's own encoder (no extra dependencies). */
async function toWebp(encoder: Page, png: Buffer): Promise<Buffer> {
  const dataUrl = await encoder.evaluate(
    async ({ source, q }) => {
      const image = new Image();
      image.src = source;
      await image.decode();
      const canvas = document.createElement("canvas");
      canvas.width = image.naturalWidth;
      canvas.height = image.naturalHeight;
      canvas.getContext("2d")?.drawImage(image, 0, 0);
      return canvas.toDataURL("image/webp", q);
    },
    { source: `data:image/png;base64,${png.toString("base64")}`, q: quality },
  );
  return Buffer.from(dataUrl.slice(dataUrl.indexOf(",") + 1), "base64");
}

const written: { file: string; bytes: number; sha256: string }[] = [];
let encoder: Page;

async function capture(page: Page, name: string): Promise<void> {
  await page.evaluate(() => document.fonts.ready);
  await page.waitForLoadState("networkidle", { timeout: 15_000 });
  await page.waitForTimeout(150);
  const file = join(outDir, `${name}.webp`);
  const bytes = await toWebp(
    encoder,
    await page.screenshot({ animations: "disabled", caret: "hide" }),
  );
  // Record metadata from the exact buffer we write. Do not stat and then reopen a path
  // that another process could replace between the two filesystem operations.
  writeFileSync(file, bytes);
  written.push({
    file: `${name}.webp`,
    bytes: bytes.length,
    sha256: createHash("sha256").update(bytes).digest("hex"),
  });
  console.log(`  ${name}.webp  ${(bytes.length / 1024).toFixed(0)} kB`);
}

async function newPage(
  browser: Browser,
  viewport: { width: number; height: number },
  scheme: "light" | "dark",
  person?: Account,
): Promise<{ context: BrowserContext; page: Page }> {
  const context = await browser.newContext({
    baseURL,
    viewport,
    colorScheme: scheme,
    reducedMotion: "reduce",
    locale: "en-GB",
    ...(viewport.width < 600
      ? { isMobile: true, hasTouch: true, deviceScaleFactor: 1 }
      : {}),
  });
  if (person) {
    const api = new Api(context.request);
    await api.call("/auth/login", "POST", {
      email: person.email,
      password: person.password,
    });
    await api.call("/auth/preferences", "PUT", {
      language: "EN",
      theme: scheme,
      onboardingCompleted: true,
    });
  }
  const page = await context.newPage();
  page.on("pageerror", (error) => {
    throw error;
  });
  await page.goto("/");
  return { context, page };
}

/** Direct routes are deliberately reloaded: these are screenshots of the served app. */
async function openView(page: Page, route: string): Promise<void> {
  await page.goto("/" + route);
  await page.locator("#workspace-page h1").first().waitFor();
}

async function main(): Promise<void> {
  mkdirSync(outDir, { recursive: true });
  const browser = await launchBrowser();
  console.log(`Seeding demo data on ${baseURL}`);
  try {
    const data = await seed();
    encoder = await browser.newPage();
    console.log(`Writing screenshots to ${outDir}`);
    const anonymous = await newPage(browser, desktop, "light");
    await anonymous.page.locator('input[type="password"]').first().waitFor();
    await capture(anonymous.page, "sign-in");
    await anonymous.page.goto(data.reportPath);
    await anonymous.page.getByRole("heading", { level: 1 }).first().waitFor();
    await capture(anonymous.page, "shared-report");
    await anonymous.context.close();

    // A second member on the same board, so realtime presence (B1) has someone to show.
    const colleague = await newPage(browser, desktop, "light", data.developer);
    await openView(colleague.page, "tasks");

    const { context, page } = await newPage(
      browser,
      desktop,
      "light",
      data.owner,
    );
    for (const [route, name] of [
      ["overview", "overview"],
      ["projects", "projects"],
      ["tasks", "board"],
      ["backlog", "backlog"],
      ["sprints", "sprints"],
      ["roadmap", "roadmap"],
      ["metrics", "metrics"],
      ["flow", "flow"],
      ["forecast", "forecast"],
      ["dashboard", "dashboard"],
      [`poker?session=${data.pokerId}`, "poker"],
      [`time?sprint=${data.sprintId}`, "time"],
      ["fields", "custom-fields"],
      ["calendar", "calendar"],
      ["retrospective", "retrospective"],
      ["updates", "activity"],
      ["trash", "trash"],
      ["profile", "profile"],
    ] as const) {
      await openView(page, route);
      await capture(page, name);
    }
    await page.getByRole("tab", { name: "Settings", exact: true }).click();
    await page
      .getByRole("heading", { name: "Email summaries", exact: true })
      .waitFor();
    await capture(page, "email-preferences");

    await openView(page, "tasks");
    const card = page
      .locator(".task-board article")
      .filter({ hasText: data.commentedTask });
    await card.getByRole("button", { name: "Comments", exact: true }).click();
    await page
      .getByRole("dialog")
      .getByText("Copy updated in both languages.", { exact: true })
      .waitFor();
    await capture(page, "task-comments");
    await page.keyboard.press("Escape");

    await openView(page, "overview");
    await page.keyboard.press("Control+k");
    const palette = page.getByRole("dialog");
    await palette.waitFor();
    await palette
      .getByRole("combobox", { name: "Search or run a command", exact: true })
      .fill("challenge");
    await palette
      .getByRole("option")
      .filter({ hasText: data.commentedTask })
      .first()
      .waitFor();
    await capture(page, "search-palette");
    await page.keyboard.press("Escape");
    await context.close();
    await colleague.context.close();

    const dark = await newPage(browser, desktop, "dark", data.owner);
    await openView(dark.page, "tasks");
    await capture(dark.page, "board-dark");
    await openView(dark.page, "metrics");
    await capture(dark.page, "metrics-dark");
    await dark.context.close();

    const phone = await newPage(browser, mobile, "light", data.owner);
    await openView(phone.page, "overview");
    await capture(phone.page, "mobile-overview");
    await phone.page.getByRole("button", { name: "Open menu" }).click();
    await phone.page
      .getByRole("navigation", { name: "Mobile navigation" })
      .getByRole("link", { name: "Tasks", exact: true })
      .click();
    await phone.page.locator("#workspace-page h1").waitFor();
    await capture(phone.page, "mobile-board");
    await phone.context.close();
  } finally {
    await browser.close();
  }
  const total = written.reduce((sum, entry) => sum + entry.bytes, 0);
  writeFileSync(
    join(outDir, "manifest.json"),
    JSON.stringify(
      {
        generatedAt: new Date().toISOString(),
        sourceCommit: execFileSync("git", ["rev-parse", "HEAD"], {
          cwd: root,
          encoding: "utf8",
        }).trim(),
        images: written,
      },
      null,
      2,
    ) + "\n",
  );
  console.log(
    `${written.length} screenshots, ${(total / 1024 / 1024).toFixed(2)} MB in total.`,
  );
}

await main();
