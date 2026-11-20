import { createReadStream } from "node:fs";
import { mkdir, writeFile } from "node:fs/promises";
import { createHash } from "node:crypto";
import { resolve, join } from "node:path";
import { pathToFileURL } from "node:url";
import { StringDecoder } from "node:string_decoder";
const MAX_LINE = 1_000_000,
  MAX_BATCH = 1_000_000,
  MAX_ROWS = 200;
export const provenance = {
  title: "The Public Jira Dataset",
  version: "2025-06-23 (v7)",
  doi: "10.5281/zenodo.15719919",
  url: "https://zenodo.org/records/15719919",
  license: "CC-BY-4.0",
  archive: "2025-06-23 ThePublicJiraDataset.zip",
  archiveMd5: "02f85309d966092ea130ca0797aea795",
  attribution:
    "Montgomery et al., The Public Jira Dataset (MSR 2022), version 7 (2025).",
};
type Options = {
  input: string;
  output: string;
  projects: string[];
  limit: number;
  maxInputBytes?: number;
};
type Json = Record<string, unknown>;
const object = (v: unknown): Json =>
  v !== null && typeof v === "object" && !Array.isArray(v) ? (v as Json) : {};
const string = (v: unknown) => (typeof v === "string" ? v : "");
function description(v: unknown, depth = 0): string {
  if (typeof v === "string") return v;
  if (depth > 16) throw new Error("Description nesting exceeds 16.");
  const n = object(v);
  const text =
    string(n.text) +
    (Array.isArray(n.content)
      ? n.content.map((c) => description(c, depth + 1)).join("")
      : "") +
    (n.type === "paragraph" ? "\n" : "");
  if (text.length > 8192)
    throw new Error("Description exceeds 8192 characters.");
  return text;
}
/** Only task-relevant fields leave the source file; anonymous identities never become members. */
export function projectIssue(raw: unknown): Json {
  const issue = object(raw),
    f = object(issue.fields);
  const key = string(issue.key);
  if (!key || !string(f.summary))
    throw new Error("Selected issue needs key and fields.summary.");
  const fields: Json = {
    summary: string(f.summary),
    description: description(f.description),
    status: { name: string(object(f.status).name) },
    created: string(f.created),
    resolutiondate: string(f.resolutiondate),
    duedate: string(f.duedate),
    labels: Array.isArray(f.labels) ? f.labels.map(string).filter(Boolean) : [],
  };
  for (const value of Object.values(fields))
    if (typeof value === "string" && value.length > 8192)
      throw new Error(`${key}: field exceeds 8192 characters.`);
  // Keep only dated status transitions used to derive the first In Progress milestone.
  const histories = object(issue.changelog).histories;
  const changelog = {
    histories: Array.isArray(histories)
      ? histories
          .map((h) => {
            const item = object(h);
            return {
              created: string(item.created),
              items: Array.isArray(item.items)
                ? item.items
                    .map(object)
                    .filter((i) => i.field === "status")
                    .map((i) => ({
                      field: "status",
                      fromString: string(i.fromString),
                      toString: string(i.toString),
                    }))
                : [],
            };
          })
          .filter((h) => h.items.length > 0)
      : [],
  };
  return { key, fields, changelog };
}
export async function convert(options: Options) {
  if (
    !Number.isInteger(options.limit) ||
    options.limit < 1 ||
    options.limit > 2000 ||
    !options.projects.length ||
    options.projects.some((p) => !p.match(/^[A-Z][A-Z0-9_]{0,30}$/))
  )
    throw new Error("Select project keys and a limit from 1 to 2000.");
  const maxInput = options.maxInputBytes ?? 268_435_456;
  if (
    !Number.isSafeInteger(maxInput) ||
    maxInput < 1 ||
    maxInput > 5_368_709_120
  )
    throw new Error("Input bound must be 1 byte–5 GiB.");
  const selected = new Map(options.projects.map((p) => [p, [] as Json[]]));
  const hash = createHash("sha256");
  const decoder = new StringDecoder("utf8");
  let pending = "",
    bytes = 0,
    count = 0,
    line = 0,
    projectedBytes = 0;
  function consume(text: string) {
    line++;
    if (!text.trim()) return;
    if (Buffer.byteLength(text) > MAX_LINE)
      throw new Error(`Line ${line} exceeds 1 MB.`);
    let parsed: unknown;
    try {
      parsed = JSON.parse(text);
    } catch {
      throw new Error(`Line ${line} is not MongoDB JSON Lines.`);
    }
    const issue = object(parsed);
    const key = string(issue.key);
    const project =
      string(object(object(issue.fields).project).key) ||
      key.split("-")[0] ||
      "";
    const rows = selected.get(project);
    if (!rows) return;
    if (count >= options.limit)
      throw new Error(
        "Selected issue limit exceeded. Narrow the mongoexport query rather than silently sampling.",
      );
    const projected = projectIssue(parsed);
    projectedBytes += Buffer.byteLength(JSON.stringify(projected));
    if (projectedBytes > 33_554_432)
      throw new Error(
        "Selected output exceeds 32 MiB. Narrow the export query.",
      );
    rows.push(projected);
    count++;
  }
  for await (const chunk of createReadStream(options.input)) {
    bytes += chunk.length;
    if (bytes > maxInput)
      throw new Error("Input byte limit exceeded. Export a narrower subset.");
    hash.update(chunk);
    pending += decoder.write(chunk);
    let end;
    while ((end = pending.indexOf("\n")) >= 0) {
      consume(pending.slice(0, end));
      pending = pending.slice(end + 1);
    }
    if (Buffer.byteLength(pending) > MAX_LINE)
      throw new Error(`Line ${line + 1} exceeds 1 MB.`);
  }
  pending += decoder.end();
  if (pending) consume(pending);
  if (count === 0)
    throw new Error("No issues matched the selected project keys.");
  // Validate and assemble every bounded batch before creating output; no partial conversion on bad input.
  const outputs: {
    name: string;
    content: string;
    project: string;
    rows: number;
  }[] = [];
  for (const [project, rows] of selected) {
    let batch: Json[] = [];
    let serial = 1;
    const flush = () => {
      if (batch.length) {
        outputs.push({
          name: `${project}-${String(serial++).padStart(3, "0")}.json`,
          content: JSON.stringify({ issues: batch }),
          project,
          rows: batch.length,
        });
        batch = [];
      }
    };
    for (const issue of rows) {
      if (Buffer.byteLength(JSON.stringify({ issues: [issue] })) > MAX_BATCH)
        throw new Error("An individual issue exceeds the import bound.");
      if (
        batch.length >= MAX_ROWS ||
        Buffer.byteLength(JSON.stringify({ issues: [...batch, issue] })) >
          MAX_BATCH
      )
        flush();
      batch.push(issue);
    }
    flush();
  }
  await mkdir(options.output, { recursive: false });
  for (const file of outputs)
    await writeFile(join(options.output, file.name), file.content, {
      flag: "wx",
      mode: 0o600,
    });
  const manifest = {
    ...provenance,
    sourceSha256: hash.digest("hex"),
    sourceBytes: bytes,
    selectedProjects: options.projects,
    issueCount: count,
    files: outputs.map((f) => ({
      name: f.name,
      project: f.project,
      rows: f.rows,
      sha256: createHash("sha256").update(f.content).digest("hex"),
    })),
    scope:
      "Task-only projection. Identity objects, comments, arbitrary custom fields and attachment URLs are excluded. Status milestones are summaries, not complete transition history. Free text may still contain personal data. Each source project must be imported into a separately selected SprintFlow project.",
  };
  await writeFile(
    join(options.output, "manifest.json"),
    JSON.stringify(manifest, null, 2) + "\n",
    { flag: "wx", mode: 0o600 },
  );
  return manifest;
}
if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(resolve(process.argv[1])).href
) {
  const args = process.argv.slice(2);
  const value = (key: string) => {
    const i = args.indexOf(key);
    return i < 0 ? "" : (args[i + 1] ?? "");
  };
  try {
    const input = value("--input"),
      output = value("--output");
    if (!input || !output || !value("--projects"))
      throw new Error(
        "Usage: node scripts/data/apache-subset.ts --input apache.jsonl --output new-directory --projects HADOOP,SPARK --limit 1000 [--max-input-bytes 268435456]",
      );
    const manifest = await convert({
      input,
      output,
      projects: value("--projects").split(","),
      limit: Number(value("--limit") || 1000),
      ...(value("--max-input-bytes")
        ? { maxInputBytes: Number(value("--max-input-bytes")) }
        : {}),
    });
    console.log(
      `Converted ${manifest.issueCount} issues to ${manifest.files.length} bounded Jira JSON files.`,
    );
  } catch (error) {
    console.error(
      error instanceof Error ? error.message : "Conversion failed.",
    );
    process.exitCode = 1;
  }
}
