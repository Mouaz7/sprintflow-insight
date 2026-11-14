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
const workflows = readdirSync(join(root, '.github/workflows')).filter(name => name.endsWith('.yml'));
for (const file of ['Dockerfile', 'backend/mvnw', ...workflows.map(name => `.github/workflows/${name}`)]) {
  assert.ok(!readFileSync(join(root, file)).includes(13), `Use LF line endings: ${file}`);
}
const tracked = execFileSync(git, ['-C', root, 'ls-files', '-z'], { encoding: 'utf8' }).split('\0');
for (const name of tracked) {
  assert.ok(name !== '.env' && !['backend/data/', 'backend/target/', 'frontend/node_modules/', 'frontend/dist/', 'scripts/node_modules/'].some(prefix => name.startsWith(prefix)), name);
  assert.ok(!basename(name).startsWith('client_secret'), name);
  assert.ok(!/\.(?:py|js|cjs|mjs)$/.test(name), "Use TypeScript for tracked verification/source code: " + name);
}
console.log('PASS: relative documentation links, Linux line endings, private/runtime file exclusions.');
