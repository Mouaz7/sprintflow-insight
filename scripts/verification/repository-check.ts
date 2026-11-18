import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { basename, dirname, join, resolve, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const gitIndex = process.argv.indexOf('--git');
const git = gitIndex === -1 ? 'git' : process.argv[gitIndex + 1];
assert.ok(git, 'Missing executable after --git.');
function markdownFiles(directory: string): string[] {
  return readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const path = join(directory, entry.name);
    return entry.isDirectory() ? markdownFiles(path) : entry.name.endsWith('.md') ? [path] : [];
  });
}
const missing: string[] = [];
const files = ['README.md', 'frontend/README.md', 'backend/README.md'].map(path => join(root, path));
files.push(...markdownFiles(join(root, 'docs')));
for (const file of files) {
  for (const match of readFileSync(file, 'utf8').matchAll(/\]\(([^)]+)\)/g)) {
    const target = match[1];
    if (/^(https?:\/\/|mailto:|#)/.test(target)) continue;
    if (!existsSync(resolve(dirname(file), target.split('#', 1)[0]))) missing.push(`${relative(root, file)}: ${target}`);
  }
}
assert.deepEqual(missing, [], 'Broken relative documentation links.');
// The short-lived picomatch advisory review only applies to development tooling.
// Fail if a future lockfile change moves it into any production dependency tree.
for (const lockfile of ['frontend/package-lock.json', 'frontend/e2e/package-lock.json', 'scripts/package-lock.json']) {
  const lock = JSON.parse(readFileSync(join(root, lockfile), 'utf8')) as {
    packages: Record<string, { dev?: boolean }>;
  };
  for (const [name, dependency] of Object.entries(lock.packages)) {
    if (/(^|\/)node_modules\/picomatch$/.test(name)) {
      assert.equal(dependency.dev, true, `picomatch must remain development-only: ${lockfile}:${name}. Reassess GHSA-fc5q-39xc-vjpf before changing the boundary.`);
    }
  }
}
const workflows = readdirSync(join(root, '.github/workflows')).filter(name => name.endsWith('.yml'));
const actionDirectory = join(root, '.github/actions');
const actions = existsSync(actionDirectory)
  ? readdirSync(actionDirectory).map(name => `.github/actions/${name}/action.yml`).filter(path => existsSync(join(root, path)))
  : [];
const automation = [...workflows.map(name => `.github/workflows/${name}`), ...actions, '.github/dependabot.yml'];
for (const file of ['Dockerfile', 'backend/mvnw', 'start.sh', 'scripts/env-bootstrap.sh', ...automation]) {
  assert.ok(!readFileSync(join(root, file)).includes(13), `Use LF line endings: ${file}`);
}
// Third-party actions must be pinned to a full commit SHA with a version comment.
const unpinned: string[] = [];
for (const file of [...workflows.map(name => `.github/workflows/${name}`), ...actions]) {
  for (const [index, line] of readFileSync(join(root, file), 'utf8').split('\n').entries()) {
    const use = /^\s*(?:-\s*)?uses:\s*(\S+)(.*)$/.exec(line);
    if (!use || use[1].startsWith('./')) continue;
    if (!/^[\w.-]+\/[\w./-]+@[0-9a-f]{40}$/.test(use[1]) || !/#\s*v?\d/.test(use[2])) unpinned.push(`${file}:${index + 1}: ${use[1]}`);
  }
}
assert.deepEqual(unpinned, [], 'Pin actions to a commit SHA with a version comment.');
// Base images are pinned by digest.
for (const line of readFileSync(join(root, 'Dockerfile'), 'utf8').split('\n').filter(line => /^FROM\s/i.test(line))) {
  assert.match(line, /@sha256:[0-9a-f]{64}\b/, `Pin base images by digest: ${line}`);
}
const tracked = execFileSync(git, ['-C', root, 'ls-files', '-z'], { encoding: 'utf8' }).split('\0');
for (const name of tracked) {
  assert.ok(name !== '.env' && !['backend/data/', 'backend/target/', 'frontend/node_modules/', 'frontend/dist/', 'scripts/node_modules/'].some(prefix => name.startsWith(prefix)), name);
  assert.ok(!basename(name).startsWith('client_secret'), name);
  assert.ok(!/\.(?:py|js|cjs|mjs)$/.test(name), "Use TypeScript for tracked verification/source code: " + name);
}
console.log('PASS: relative documentation links, development-only advisory boundary, Linux line endings, private/runtime file exclusions, pinned actions and base images.');
