import { readFileSync, writeFileSync } from 'node:fs';

const [input, output] = process.argv.slice(2);
if (!input || !output)
  throw new Error('Provide the CSV input and JSON output paths.');

const expectedHeader = Array.from(
  { length: 10 },
  (_, index) => `q${index + 1}`,
).join(',');
const [header, ...rows] = readFileSync(input, 'utf8').trim().split(/\r?\n/);
if (header !== expectedHeader)
  throw new Error('Expected ten columns named q1 to q10.');

const scores = rows.map((row, index) => {
  const answers = row.split(',');
  if (
    answers.length !== 10 ||
    answers.some((answer) => !/^[1-5]$/.test(answer))
  )
    throw new Error(`Invalid response on row ${index + 2}.`);
  return (
    2.5 *
    answers.reduce(
      (total, answer, column) =>
        total + (column % 2 === 0 ? Number(answer) - 1 : 5 - Number(answer)),
      0,
    )
  );
});
const sampleSize = scores.length;
const result =
  sampleSize < 5
    ? { sampleSize, status: 'insufficient-participants' }
    : {
        sampleSize,
        status: 'scored',
        mean: scores.reduce((sum, score) => sum + score, 0) / sampleSize,
        minimum: Math.min(...scores),
        maximum: Math.max(...scores),
      };
writeFileSync(output, JSON.stringify(result, null, 2) + '\n');
