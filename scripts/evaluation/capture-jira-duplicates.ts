import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { setTimeout as delay } from "node:timers/promises";

const source = "https://issues.apache.org/jira/rest/api/2/search";
const projects = ["HADOOP", "HBASE", "KAFKA"];
const seed = 20261121;
let randomState = seed;
function randomIndex(length: number) {
  randomState = (Math.imul(randomState, 1_664_525) + 1_013_904_223) >>> 0;
  return Math.floor((randomState / 2 ** 32) * length);
}

type Issue = {
  key: string;
  fields: {
    summary: string;
    created: string;
    issuelinks: {
      type: { name: string };
      inwardIssue?: Linked;
      outwardIssue?: Linked;
    }[];
  };
};
type Linked = { key: string; fields: { summary: string } };
type Page = { total: number; startAt: number; issues: Issue[] };
type Pair = { project: string; duplicate: number; a: string; b: string };
type Score = Pick<Pair, "project" | "duplicate"> & { score: number };

const jql = (project: string) =>
  `project = ${project} AND issueLinkType = duplicates AND created >= 2019-01-01 AND ` +
  "created < 2025-01-01 ORDER BY created ASC, key ASC";

async function issues(project: string) {
  const result: Issue[] = [];
  const keys = new Set<string>();
  let total: number | undefined;
  do {
    const params = new URLSearchParams({
      jql: jql(project),
      startAt: String(result.length),
      maxResults: "50",
      fields: "summary,created,issuelinks",
    });
    const response = await fetch(`${source}?${params}`, {
      signal: AbortSignal.timeout(30_000),
      redirect: "error",
    });
    if (!response.ok || !response.body)
      throw new Error(`Jira HTTP ${response.status}`);
    const stream = response.body.getReader();
    const chunks: Uint8Array[] = [];
    let bytes = 0;
    for (;;) {
      const { value, done } = await stream.read();
      if (done) break;
      bytes += value.length;
      if (bytes > 4_000_000) {
        await stream.cancel();
        throw new Error("Jira page exceeded 4 MB.");
      }
      chunks.push(value);
    }
    const page = JSON.parse(Buffer.concat(chunks).toString("utf8")) as Page;
    total ??= page.total;
    if (
      total > 1600 ||
      page.total !== total ||
      page.startAt !== result.length ||
      (!page.issues.length && result.length !== total)
    )
      throw new Error(`Incomplete Jira result for ${project}.`);
    for (const issue of page.issues) {
      if (!issue.key.startsWith(`${project}-`) || keys.has(issue.key))
        throw new Error("Unexpected Jira search key.");
      keys.add(issue.key);
      result.push(issue);
    }
    if (result.length < total) await delay(500);
  } while (result.length < total);
  return result;
}

function pairs(project: string, rows: Issue[]) {
  const known = new Set<string>();
  const hits: Pair[] = [];
  const sources: Issue[] = [];
  const keyOf = (left: string, right: string) => [left, right].sort().join(":");
  for (const row of rows) {
    for (const link of row.fields.issuelinks) {
      const peer = link.outwardIssue ?? link.inwardIssue;
      if (
        link.type.name !== "Duplicate" ||
        !peer?.key.startsWith(`${project}-`)
      )
        continue;
      const key = keyOf(row.key, peer.key);
      if (known.has(key)) continue;
      known.add(key);
      hits.push({
        project,
        duplicate: 1,
        a: row.fields.summary,
        b: peer.fields.summary,
      });
      sources.push(row);
    }
  }
  const positives = hits.length;
  for (const row of sources) {
    const at = Date.parse(row.fields.created);
    const peers = rows
      .filter(
        (peer) => peer.key !== row.key && !known.has(keyOf(row.key, peer.key)),
      )
      .sort(
        (a, b) =>
          Math.abs(Date.parse(a.fields.created) - at) -
            Math.abs(Date.parse(b.fields.created) - at) ||
          a.key.localeCompare(b.key, "en"),
      );
    for (let i = 0; i < 3 && peers.length; i++) {
      const [peer] = peers.splice(randomIndex(Math.min(peers.length, 20)), 1);
      known.add(keyOf(row.key, peer.key));
      hits.push({
        project,
        duplicate: 0,
        a: row.fields.summary,
        b: peer.fields.summary,
      });
    }
  }
  return {
    hits,
    cohort: {
      project,
      jql: jql(project),
      issues: rows.length,
      positives,
      controls: hits.length - positives,
    },
  };
}

function score(url: string, pairs: Pair[]): Score[] {
  // Only base64 literals are interpolated. Original issue text never reaches the output files.
  const data = Buffer.from(JSON.stringify(pairs)).toString("base64");
  const sql = `select coalesce(json_agg(scored), '[]') from (
    select project,duplicate,task_duplicate_score(a,b) as score
    from jsonb_to_recordset(convert_from(decode('${data}','base64'),'UTF8')::jsonb)
      as pairs(project text,duplicate integer,a text,b text)
    ) scored;`;
  const command = spawnSync(
    "psql",
    ["--no-psqlrc", "--dbname", url, "-v", "ON_ERROR_STOP=1", "-At"],
    { input: sql, encoding: "utf8", maxBuffer: 2_000_000 },
  );
  if (command.status !== 0)
    throw new Error(
      "Could not run task_duplicate_score against the evaluation database.",
    );
  return JSON.parse(command.stdout) as Score[];
}

function evaluate(scores: Score[], threshold: number) {
  let truePositive = 0;
  let falsePositive = 0;
  let falseNegative = 0;
  for (const item of scores) {
    if (item.score >= threshold) {
      if (item.duplicate) truePositive++;
      else falsePositive++;
    } else if (item.duplicate) falseNegative++;
  }
  const precision = truePositive / (truePositive + falsePositive || 1);
  const recall = truePositive / (truePositive + falseNegative || 1);
  const f1 = (2 * precision * recall) / (precision + recall || 1);
  return {
    threshold,
    truePositive,
    falsePositive,
    falseNegative,
    precision,
    recall,
    f1,
  };
}

function calibrate(observations: Score[]) {
  const training = observations.filter((row) => row.project !== "KAFKA");
  const test = observations.filter((row) => row.project === "KAFKA");
  const grid = Array.from({ length: 33 }, (_, i) => (50 + i * 25) / 1000);
  const curve = grid.map((threshold) => evaluate(training, threshold));
  const selected = curve
    .filter((point) => point.threshold >= 0.325 && point.precision >= 0.9)
    .sort(
      (a, b) =>
        b.recall - a.recall ||
        b.precision - a.precision ||
        b.threshold - a.threshold,
    )[0];
  if (!selected) throw new Error("No threshold met the precision target.");
  return { selected, holdout: evaluate(test, selected.threshold), curve };
}

const directory = process.argv[2];
if (directory === "--replay" && process.argv[3]) {
  const csv = await readFile(process.argv[3], "utf8");
  const rows = csv
    .trim()
    .split("\n")
    .slice(1)
    .map((line) => {
      const [project, duplicate, score] = line.split(",");
      return { project, duplicate: Number(duplicate), score: Number(score) };
    });
  process.stdout.write(`${JSON.stringify(calibrate(rows), null, 2)}\n`);
  process.exit(0);
}
const database = process.env.C4_EVALUATION_PG_URL;
if (!directory || !database)
  throw new Error(
    "Set C4_EVALUATION_PG_URL and run capture-jira-duplicates.ts OUTPUT_DIRECTORY.",
  );
const cohorts: ReturnType<typeof pairs>["cohort"][] = [];
const observations: Score[] = [];
for (const project of projects) {
  const { hits, cohort } = pairs(project, await issues(project));
  if (!cohort.positives || !cohort.controls)
    throw new Error(`Missing labelled pairs for ${project}.`);
  cohorts.push(cohort);
  observations.push(...score(database, hits));
}

const { selected, holdout, curve } = calibrate(observations);
const lines = observations
  .map(
    ({ project, duplicate, score }) =>
      `${project},${duplicate},${score.toFixed(8)}`,
  )
  .sort();
const csv = ["project,duplicate,score", ...lines].join("\n") + "\n";
const manifest = {
  source,
  capturedAt: new Date().toISOString(),
  seed,
  cohorts,
  selected,
  holdout,
  sha256: createHash("sha256").update(csv).digest("hex"),
};
await mkdir(directory, { recursive: true });
await writeFile(join(directory, "jira-duplicate-scores.csv"), csv, {
  flag: "wx",
});
const points = curve.map(
  (item) => `${item.threshold},${item.precision},${item.recall},${item.f1}`,
);
await writeFile(
  join(directory, "jira-duplicate-curve.csv"),
  ["threshold,precision,recall,f1", ...points].join("\n") + "\n",
  { flag: "wx" },
);
await writeFile(
  join(directory, "jira-duplicate-source.json"),
  JSON.stringify(manifest, null, 2) + "\n",
  { flag: "wx" },
);
process.stdout.write(`${JSON.stringify(manifest, null, 2)}\n`);
