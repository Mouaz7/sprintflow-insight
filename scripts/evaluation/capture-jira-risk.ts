import { createHash } from "node:crypto";
import { mkdir, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { setTimeout as delay } from "node:timers/promises";

const source = "https://issues.apache.org/jira/rest/api/2/search";
const jql =
  "project in (DATALAB,SDAP,HDDS,MESOS,OFBIZ) AND Sprint is not EMPTY AND " +
  "created >= 2019-01-01 AND created < 2025-01-01 ORDER BY created ASC, key ASC";
const sprintField = "customfield_12310921";
const pointsField = "customfield_12310293";
const day = 86_400_000;
const nightly = (2 * 60 + 40) * 60_000;
const date = (time: number) => new Date(time).toISOString().slice(0, 10);

type Change = {
  field: string;
  from: string | null;
  to: string | null;
  fromString: string | null;
  toString: string | null;
};
type Page = {
  startAt: number;
  total: number;
  issues: {
    key: string;
    fields: {
      project: { key: string };
      created: string;
      status: { name: string };
      comment: { total: number; comments: { created: string }[] } | null;
      [sprintField]: string[] | null;
      [pointsField]: number | null;
    };
    changelog: {
      total: number;
      histories: { id: string; created: string; items: Change[] }[];
    };
  }[];
};
type Sprint = { id: string; started: number; ended: number };
type Event = { at: number; change: Change };
type Issue = {
  project: string;
  created: number;
  status: string;
  points: number;
  sprints: Sprint[];
  events: Event[];
  comments: number[];
};

function closedSprint(text: string): Sprint | undefined {
  const field = (key: string) =>
    new RegExp(`(?:^|[\\[,])${key}=([^,\\]]+)`).exec(text)?.[1];
  const id = field("id");
  const started = Date.parse(
    field("activatedDate") ?? field("startDate") ?? "",
  );
  const planned = Date.parse(field("endDate") ?? "");
  const closed = Date.parse(field("completeDate") ?? "");
  const ended = Math.min(planned, closed);
  if (id && field("state") === "CLOSED" && ended > started)
    return { id, started, ended };
}

async function capture() {
  const issues: Issue[] = [];
  const seen = new Set<string>();
  const counts = {
    matchedIssues: 0,
    incompleteHistories: 0,
    incompleteComments: 0,
    withoutClosedSprint: 0,
  };
  let startAt = 0;
  do {
    const params = new URLSearchParams({
      jql,
      startAt: String(startAt),
      maxResults: "50",
      fields: `project,created,status,comment,${sprintField},${pointsField}`,
      expand: "changelog",
    });
    const response = await fetch(`${source}?${params}`, {
      signal: AbortSignal.timeout(30_000),
      redirect: "error",
    });
    if (!response.ok || !response.body)
      throw new Error(`Jira HTTP ${response.status}`);
    const stream = response.body.getReader();
    const chunks: Uint8Array[] = [];
    let size = 0;
    for (;;) {
      const { done, value } = await stream.read();
      if (done) break;
      size += value.byteLength;
      if (size > 8_000_000) {
        await stream.cancel();
        throw new Error("Jira response exceeded 8 MB for one page.");
      }
      chunks.push(value);
    }
    const result = JSON.parse(Buffer.concat(chunks).toString("utf8")) as Page;
    if (startAt === 0) counts.matchedIssues = result.total;
    if (
      result.total > 5_000 ||
      result.total !== counts.matchedIssues ||
      result.startAt !== startAt ||
      !result.issues.length
    )
      throw new Error(
        `Incomplete or excessive Jira results at offset ${startAt}.`,
      );

    for (const row of result.issues) {
      if (seen.has(row.key))
        throw new Error("Repeated Jira issue across pages.");
      seen.add(row.key);
      const { changelog, fields } = row;
      if (changelog.histories.length !== changelog.total) {
        counts.incompleteHistories++;
        continue;
      }
      if (
        fields.comment &&
        fields.comment.comments.length !== fields.comment.total
      ) {
        counts.incompleteComments++;
        continue;
      }
      const sprints = (fields[sprintField] ?? []).flatMap(
        (text) => closedSprint(text) ?? [],
      );
      if (!sprints.length) {
        counts.withoutClosedSprint++;
        continue;
      }
      const events = [...changelog.histories]
        .sort(
          (a, b) =>
            Date.parse(a.created) - Date.parse(b.created) ||
            Number(a.id) - Number(b.id),
        )
        .flatMap((history) =>
          history.items.map((change) => ({
            at: Date.parse(history.created),
            change,
          })),
        )
        .filter(({ change }) =>
          ["status", "Sprint", "assignee", "Flagged", "Story Points"].includes(
            change.field,
          ),
        );
      issues.push({
        project: fields.project.key,
        created: Date.parse(fields.created),
        status: fields.status.name,
        points: fields[pointsField] ?? 0,
        sprints,
        events,
        comments:
          fields.comment?.comments.map((comment) =>
            Date.parse(comment.created),
          ) ?? [],
      });
    }
    startAt += result.issues.length;
    if (startAt < counts.matchedIssues) await delay(500);
  } while (startAt < counts.matchedIssues);
  return { issues, counts };
}

function previousValue<T>(
  issue: Issue,
  at: number,
  field: string,
  current: T,
  previous: (change: Change) => T,
): T {
  const future = issue.events.find(
    (event) => event.at > at && event.change.field === field,
  );
  return future ? previous(future.change) : current;
}

const statusAt = (issue: Issue, at: number) =>
  previousValue(
    issue,
    at,
    "status",
    issue.status,
    (change) => change.fromString ?? "Open",
  );
const done = (status: string) =>
  /^(?:closed|resolved|done|completed)$/i.test(status);

function sprintContains(issue: Issue, sprint: Sprint, at: number) {
  if (issue.created > at || sprint.started > at || sprint.ended <= at)
    return false;
  const ids = previousValue(
    issue,
    at,
    "Sprint",
    issue.sprints.map((s) => s.id),
    (change) => change.from?.match(/\d+/g) ?? [],
  );
  return ids.includes(sprint.id);
}

function collectRows(issues: Issue[]) {
  const priorSpans = issues.flatMap((issue) => {
    let entered = issue.created;
    return issue.events
      .filter(
        ({ change }) =>
          change.field === "status" && change.fromString !== change.toString,
      )
      .filter(({ at }) => at >= issue.created)
      .map(({ at, change }) => {
        const span = {
          project: issue.project,
          status: change.fromString,
          ended: at,
          hours: (at - entered) / 3_600_000,
        };
        entered = at;
        return span;
      });
  });
  return issues
    .flatMap((issue) =>
      issue.sprints.flatMap((sprint) => {
        const changes = issue.events.filter(({ change }) =>
          ["Sprint", "status"].includes(change.field),
        );
        const touchpoints = [
          Math.max(issue.created, sprint.started),
          ...changes.map((event) => event.at),
        ];
        const at = touchpoints
          .map((point) => Math.ceil((point - nightly) / day) * day + nightly)
          .filter(
            (sample) =>
              sprintContains(issue, sprint, sample) &&
              !done(statusAt(issue, sample)),
          )
          .sort((a, b) => a - b)[0];
        if (at === undefined) return [];
        const status = statusAt(issue, at);
        const history = issue.events.filter((event) => event.at <= at);
        const entered =
          history
            .filter(
              ({ change }) =>
                change.field === "status" &&
                change.fromString !== change.toString,
            )
            .at(-1)?.at ?? issue.created;
        const ages = priorSpans
          .filter(
            (span) =>
              span.project === issue.project &&
              span.status === status &&
              span.ended <= at &&
              span.ended >= at - 180 * day,
          )
          .map((span) => span.hours)
          .sort((a, b) => a - b);
        const index = 0.85 * (ages.length - 1);
        const lower = ages[Math.floor(index)];
        const higher = ages[Math.ceil(index)];
        const ratio =
          lower === undefined || higher === undefined
            ? 1
            : (at - entered) /
              3_600_000 /
              Math.max(
                1,
                lower + (index - Math.floor(index)) * (higher - lower),
              );
        const blocked = new Set(
          history
            .filter(
              ({ change }) =>
                (change.field === "status" &&
                  /^blocked$/i.test(change.toString ?? "")) ||
                (change.field === "Flagged" &&
                  /impediment/i.test(change.toString ?? "")),
            )
            .map(({ at }) => at),
        ).size;
        const assignments = history.filter(
          ({ change }) =>
            change.field === "assignee" && change.from !== change.to,
        ).length;
        const commentCount = issue.comments.filter(
          (time) => time > at - 7 * day && time <= at,
        ).length;
        const points = previousValue(
          issue,
          at,
          "Story Points",
          issue.points,
          (change) => Number(change.fromString ?? 0),
        );
        const daysLeft = Math.floor(sprint.ended / day) - Math.floor(at / day);
        const wip = issues.filter(
          (peer) =>
            peer.project === issue.project &&
            sprintContains(peer, sprint, at) &&
            statusAt(peer, at) === status,
        ).length;
        return [
          [
            issue.project,
            date(at),
            date(sprint.ended),
            Number(!done(statusAt(issue, sprint.ended))),
            ratio.toFixed(8),
            blocked,
            assignments,
            commentCount,
            Number.isFinite(points) && points >= 0 ? points : 0,
            0,
            daysLeft,
            wip,
          ].join(","),
        ];
      }).sort((a,b) => a.localeCompare(b, "en")).slice(0,1),
    )
    .sort();
}

const output = process.argv[2];
if (!output)
  throw new Error(
    "Usage: node scripts/evaluation/capture-jira-risk.ts OUTPUT_DIRECTORY",
  );
const { issues, counts } = await capture();
const rows = collectRows(issues);
const csv =
  [
    "project,observed_utc,outcome_utc,late,age_ratio,blocks,reassignments,comments,points,depth,days_left,column_wip",
    ...rows,
  ].join("\n") + "\n";
const manifest = {
  source,
  jql,
  capturedAt: new Date().toISOString(),
  ...counts,
  eligibleIssues: issues.length,
  distinctTaskObservations: rows.length,
  sha256: createHash("sha256").update(csv).digest("hex"),
};
await mkdir(output, { recursive: true });
await writeFile(join(output, "apache-jira-risk.csv"), csv, { flag: "wx" });
await writeFile(
  join(output, "apache-jira-risk-source.json"),
  JSON.stringify(manifest, null, 2) + "\n",
  { flag: "wx" },
);
process.stdout.write(`${JSON.stringify(manifest, null, 2)}\n`);
