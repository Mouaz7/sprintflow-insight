import { createHash } from "node:crypto";
import { mkdir, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { setTimeout as delay } from "node:timers/promises";

const endpoint = "https://issues.apache.org/jira/rest/api/2/search";
const projects = ["HADOOP", "HBASE", "KAFKA"] as const;
const start = "2022-01-03";
const end = "2022-07-04";
const weeks = 26;
const weekMs = 7 * 86_400_000;
const maxIssues = 2000;
const maxPageBytes = 256_000;

type Page = {
  total: number;
  startAt: number;
  issues: { key: string; fields: { resolutiondate: string } }[];
};

async function page(params: URLSearchParams): Promise<Page> {
  const response = await fetch(`${endpoint}?${params}`, {
    headers: { Accept: "application/json" },
    signal: AbortSignal.timeout(30_000),
    redirect: "error",
  });
  if (!response.ok || !response.body)
    throw new Error(`HTTP ${response.status}`);
  const stream = response.body.getReader();
  const chunks: Uint8Array[] = [];
  let bytes = 0;
  for (;;) {
    const { done, value } = await stream.read();
    if (done) break;
    bytes += value.byteLength;
    if (bytes > maxPageBytes) {
      await stream.cancel();
      throw new Error("Jira response exceeded the page byte limit.");
    }
    chunks.push(value);
  }
  return JSON.parse(Buffer.concat(chunks).toString("utf8")) as Page;
}

async function capture(project: (typeof projects)[number]) {
  const jql =
    `project = "${project}" AND statusCategory = Done ` +
    `AND resolutiondate >= "${start}" AND resolutiondate < "${end}" ` +
    "ORDER BY resolutiondate ASC, key ASC";
  const resolved = new Map<string, string>();
  let total: number | undefined;
  let startAt = 0;
  do {
    const result = await page(
      new URLSearchParams({
        jql,
        fields: "resolutiondate",
        maxResults: "100",
        startAt: String(startAt),
      }),
    );
    total ??= result.total;
    if (
      !Number.isInteger(total) ||
      total < 0 ||
      total > maxIssues ||
      result.total !== total ||
      result.startAt !== startAt ||
      (result.issues.length === 0 && startAt < total)
    )
      throw new Error(`${project}: Incomplete or excessive Jira results.`);

    for (const issue of result.issues) {
      if (!issue.key.startsWith(`${project}-`) || resolved.has(issue.key))
        throw new Error(`${project}: Unexpected or repeated issue key.`);
      resolved.set(
        issue.key,
        new Date(issue.fields.resolutiondate).toISOString(),
      );
    }
    startAt += result.issues.length;
    if (startAt < total) await delay(500);
  } while (startAt < total);

  if (resolved.size !== total)
    throw new Error(`${project}: Incomplete issue set.`);
  const counts = Array<number>(weeks).fill(0);
  let outsideUtcWindow = 0;
  for (const timestamp of resolved.values()) {
    const index = Math.floor(
      (Date.parse(timestamp) - Date.parse(start)) / weekMs,
    );
    if (index >= 0 && index < weeks) counts[index]++;
    else outsideUtcWindow++;
  }
  // Only counts and a checksum leave this process. Issue keys and dates remain in memory.
  const sourceSha256 = createHash("sha256")
    .update(
      JSON.stringify(
        [...resolved].sort(([left], [right]) =>
          left.localeCompare(right, "en"),
        ),
      ),
    )
    .digest("hex");
  return {
    counts,
    metadata: {
      project,
      jql,
      matchedIssues: total,
      outsideUtcWindow,
      sourceSha256,
    },
  };
}

const output = process.argv[2];
if (!output)
  throw new Error(
    "Usage: node scripts/evaluation/capture-jira-throughput.ts OUTPUT_DIR",
  );
const lines = ["project,week_start_utc,resolved_issues"];
const manifest = {
  source: endpoint,
  capturedAt: "",
  startInclusiveUtc: start,
  endExclusiveUtc: end,
  projects: [] as Awaited<ReturnType<typeof capture>>["metadata"][],
};
for (const project of projects) {
  const { counts, metadata } = await capture(project);
  manifest.projects.push(metadata);
  counts.forEach((count, index) => {
    const monday = new Date(Date.parse(start) + index * weekMs)
      .toISOString()
      .slice(0, 10);
    lines.push(`${project},${monday},${count}`);
  });
}
manifest.capturedAt = new Date().toISOString();
await mkdir(output);
await writeFile(
  join(output, "apache-jira-weekly.csv"),
  `${lines.join("\n")}\n`,
  { flag: "wx" },
);
await writeFile(
  join(output, "apache-jira-source.json"),
  `${JSON.stringify(manifest, null, 2)}\n`,
  { flag: "wx" },
);
