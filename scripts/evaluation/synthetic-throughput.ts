import { writeFileSync } from 'node:fs';

const output = process.argv[2];
if (!output) throw new Error('Provide the output CSV path.');

let seed = 2026112201;
function uniform() {
  seed = (Math.imul(1664525, seed) + 1013904223) >>> 0;
  return seed / 2 ** 32;
}

function poisson(mean: number) {
  const cutoff = Math.exp(-mean);
  let product = uniform();
  let count = 0;
  while (product > cutoff) {
    count++;
    product *= uniform();
  }
  return count;
}

const processes = {
  STABLE: () => poisson(10),
  SHIFT: (week: number) => poisson(week < 13 ? 5 : 18),
  INTERMITTENT: () => (uniform() < 0.45 ? poisson(22) : 0),
};
const rows = ['project,week_start_utc,resolved_issues'];
for (const [name, sample] of Object.entries(processes)) {
  for (let week = 0; week < 26; week++) {
    const date = new Date(Date.UTC(2022, 0, 3 + 7 * week))
      .toISOString()
      .slice(0, 10);
    rows.push(`${name},${date},${sample(week)}`);
  }
}
writeFileSync(output, rows.join('\n') + '\n');
