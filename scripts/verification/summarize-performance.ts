/** Extract measured-scenario HTTP points; excludes setup/teardown and credentials. */
import { readFileSync, writeFileSync } from 'node:fs';
const [source, output, label] = process.argv.slice(2);
if (!source || !output || !label) throw new Error('Usage: summarize-performance.ts raw.json summary.json label');
const latencies: number[] = [];
const failed: number[] = [];
const timestamps: string[] = [];
for (const line of readFileSync(source, 'utf8').split('\n')) {
  if (!line.trim()) continue;
  const point = JSON.parse(line) as { type: string; metric: string; data: { value: number; time: string; tags?: { scenario?: string } } };
  if (point.type !== 'Point' || point.data.tags?.scenario !== 'measure') continue;
  if (point.metric === 'http_req_duration') { latencies.push(point.data.value); timestamps.push(point.data.time); }
  if (point.metric === 'http_req_failed') failed.push(point.data.value);
}
if (!latencies.length) throw new Error('No measured requests in the input.');
const sorted = [...latencies].sort((a, b) => a - b);
function percentile(p: number) {
  const index = (sorted.length - 1) * p;
  const lower = Math.floor(index), upper = Math.ceil(index);
  return sorted[lower] + (sorted[upper] - sorted[lower]) * (index - lower);
}
const result = {
  label,
  protocol: {
    k6: process.env.K6_BUILD_LABEL ?? '2.3.0', virtual_users: 5, measured_duration_seconds: 20, warmup_iterations: 10,
    reads_per_iteration: 3, think_time_seconds: 0.1,
    dataset: 'one Scrum project, one sprint, one story, 23 tasks',
    jvm_flags: process.env.K6_JVM_FLAGS ?? '-Xms256m -Xmx512m -XX:ActiveProcessorCount=2',
    postgres: process.env.K6_DATABASE_LABEL ?? '16.10-alpine, Docker cpus=2,memory=512m',
    endpoints: ['GET /api/projects', 'GET /api/tasks?projectId=<fixture>', 'GET /api/projects/<fixture>/metrics'],
  },
  metrics: {
    requests: sorted.length, requests_per_second: sorted.length / 20,
    failed_requests: failed.reduce((total, value) => total + value, 0),
    mean_ms: sorted.reduce((total, value) => total + value, 0) / sorted.length,
    median_ms: percentile(0.5), p90_ms: percentile(0.9), p95_ms: percentile(0.95), max_ms: sorted.at(-1),
  },
  first_measured_request: [...timestamps].sort()[0], last_measured_request: [...timestamps].sort().at(-1),
  limits: 'Single local run; host load and caches are not controlled. Upgrade acceptance evidence, not a causal scalability claim.',
};
writeFileSync(output, JSON.stringify(result, null, 2) + '\n');
console.log(JSON.stringify(result.metrics));
