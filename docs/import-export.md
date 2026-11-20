# Import, account export and research data

Open **Import & audit / Import och granskning** after selecting a project. CSV has a header row; Jira JSON accepts a root issue array or `{ "issues": [...] }`. Use at most 200 tasks and 1 MB per file. A Member, Admin or Owner can preview/import in an active project. Preview saves nothing. Map the title and any optional description/status/points/assignee/labels/dates/source key or current project custom fields. Unknown workflow states must be explicitly mapped to **To Do**, **In Progress**, **Review** or **Done**. Labels use semicolons; dates use ISO-8601 (Jira offsets such as `+0000` are accepted).

```csv
title,status,sourceKey,storyPoints
Prepare review,To Do,EXTERNAL-1,3
Finish handoff,Done,EXTERNAL-2,5
```

Review every warning and error, then confirm **Import tasks**. Mapping, membership or custom-field changes require a new preview. Any invalid row aborts the batch. A retried identical request returns the same receipt. Existing external keys are skipped, never updated. Titles/descriptions over 255 characters need correction or the explicit shortening option; originals remain in source provenance. Deleting a task permanently removes its source key; a different request can create it again. Keep import files private because original free text may contain personal data.

Owners/Admins may preserve created/started/completed dates. This affects historical task metrics and reconstructs only summary milestones. It does not restore full Jira transition/sprint/reopening history. Leave it off for normal current work. Current project members are the only possible assignees or user custom-field values; anonymous dataset IDs never create accounts.

Under **Profile → Settings → Export your account**, download the JSON ZIP. The manifest explains scope and limitations; see [ADR 0027](adr/0027-imports-account-export-and-audit.md). Store it privately. A missing stored attachment causes a retryable conflict; restore that file before retrying. A 413 means an operator must assist the complete export (default 64 MiB, maximum configurable 128 MiB and 100,000 rows per section). Account export is limited to two requests per minute. No SMTP or third-party connection is required.

Owners/Admins can read **Administrative audit log** on the import page, including archived projects. It keeps fixed action metadata for 90 days and cannot be edited through the application. It is independent of **Updates**, which remains editable. Access ends when membership/project access ends.

## Montgomery MSR 2022 Apache subset

Use the primary [dataset record](https://zenodo.org/records/15719919) and [paper](https://arxiv.org/html/2201.08368v3). Version 7 is dated **2025-06-23**, archive `2025-06-23 ThePublicJiraDataset.zip`, MD5 `02f85309d966092ea130ca0797aea795`. Attribute **Montgomery et al., The Public Jira Dataset (MSR 2022), v7 (2025), DOI 10.5281/zenodo.15719919**, data license **CC BY 4.0**; dataset scripts use **MIT**. Check the archive's licenses before redistribution. Identity fields use anonymized UUIDs; free text still needs review. This repository does not redistribute the dump or claim that all text is anonymized.

The 5.8 GB download contains a gzip MongoDB `mongodump` archive, not Jira search JSON. Restoration expands to about 60 GB; do this locally with deliberate disk allocation. Follow the archive `MongoDBDump/README.md` restore command (`mongorestore --gzip --archive=...`), then inspect the source's `JiraReposAnon` database and Apache collection. Export only selected Apache projects using a MongoDB filter; for example:

```sh
mongoexport --db=JiraReposAnon --collection=Apache \
  --query='{"fields.project.key":{"$in":["SPARK"]}}' \
  --out=apache-spark.jsonl
node scripts/data/apache-subset.ts --input apache-spark.jsonl \
  --output spark-import --projects SPARK --limit 1000
```

Use your actual MongoDB connection settings without committing credentials. The output directory must be new. Input is JSON Lines (not `--jsonArray`). Default input bound is 256 MiB, maximum selectable issue count 2,000, and projected output at most 32 MiB; narrow the Mongo query if a limit is reached. No silent first-N sampling occurs. `--max-input-bytes` can raise the streaming input bound to at most 5 GiB. Conversion excludes identity objects, comments, attachments and arbitrary custom fields; it preserves task text, source key, labels, dates and dated status-change summaries. It does not infer story-point semantics.

Each output file is at most 200 issues/1 MB, grouped by source project. `manifest.json` records declared upstream provenance, source SHA-256, selected projects, issue counts and batch SHA-256. Verify the original archive MD5 yourself; a JSONL hash does not prove archive provenance. Import each source project into its own explicitly selected SprintFlow project; map statuses in the UI and opt into summary history only when appropriate. Keep the manifest with evaluation results and disclose the reduction from full Jira history. Synthetic converter tests demonstrate shape compatibility; full dataset restoration and C2 benchmark measurements are separate work.
