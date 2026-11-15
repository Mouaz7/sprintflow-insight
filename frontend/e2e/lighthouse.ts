// Lighthouse CI with performance budgets against a running app.
// Usage: E2E_BASE_URL=http://127.0.0.1:8081 npm run lighthouse
//
// LHCI only reads JSON/YAML/JS config files, so the typed config lives here and
// is written to a temporary JSON file for `lhci autorun`.
import { spawnSync } from 'node:child_process';
import { mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

type Assertion =
  | ['error' | 'warn', { maxNumericValue: number }]
  | ['error' | 'warn', { minScore: number }];

const here = dirname(fileURLToPath(import.meta.url));
const baseUrl = process.env.E2E_BASE_URL ?? 'http://127.0.0.1:8081';

// Budgets. Field INP cannot be measured in a lab run without interaction, so
// Total Blocking Time is the lab proxy: TBT < 200 ms correlates with INP < 200 ms.
function budgets(level: 'error' | 'warn'): Record<string, Assertion> {
  return {
    'largest-contentful-paint': [level, { maxNumericValue: 2500 }],
    'total-blocking-time': [level, { maxNumericValue: 200 }],
    'cumulative-layout-shift': [level, { maxNumericValue: 0.1 }],
    // Transferred (compressed) bytes; size-limit guards the gzip size at build time.
    'resource-summary:script:size': [level, { maxNumericValue: 175_000 }],
    'categories:accessibility': ['warn', { minScore: 0.95 }],
    'categories:best-practices': ['warn', { minScore: 0.9 }],
  };
}

// Desktop budgets gate the job. The mobile profile (simulated slow 4G and a
// 4x slower CPU) is reported as warnings until the render path is lighter.
const profiles = [
  { name: 'desktop', level: 'error', settings: { preset: 'desktop' } },
  { name: 'mobile', level: 'warn', settings: {} },
] as const;

const directory = mkdtempSync(join(tmpdir(), 'sprintflow-lhci-'));
let failed = false;
for (const profile of profiles) {
  const config = {
    ci: {
      collect: {
        // The sign-in page is the public entry point and the first paint for every visit.
        url: [`${baseUrl}/`],
        numberOfRuns: 3,
        settings: {
          ...profile.settings,
          chromeFlags: '--headless=new --no-sandbox',
        },
      },
      assert: {
        assertions: budgets(profile.level),
        // Judge the median run, not the best of three.
        aggregationMethod: 'median-run',
      },
      upload: {
        target: 'filesystem',
        outputDir: join(here, 'lighthouse-report', profile.name),
      },
    },
  };
  const configPath = join(directory, `lighthouserc.${profile.name}.json`);
  writeFileSync(configPath, JSON.stringify(config, null, 2));
  console.log(`\nLighthouse CI: ${profile.name} profile (${profile.level})`);
  const result = spawnSync(
    'npx',
    ['lhci', 'autorun', `--config=${configPath}`],
    { cwd: here, stdio: 'inherit' },
  );
  if (result.status !== 0) failed = true;
}
process.exit(failed ? 1 : 0);
