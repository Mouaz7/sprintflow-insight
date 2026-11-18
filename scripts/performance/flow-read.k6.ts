// Run after flow-fixture.ts seed. Credentials are disposable and never logged.
import http from "k6/http";
import { check, sleep } from "k6";
const fixture = JSON.parse(
  open(
    __ENV.FLOW_FIXTURE_FILE || "../../artifacts/flow-performance/fixture.json",
  ),
);
if (
  fixture.source !== "b5-flow-load-v1" ||
  fixture.historyDays !== 730 ||
  fixture.tasks < 1000
)
  throw new Error("The two-year fixture is required.");
const base = __ENV.BASE_URL || fixture.base;
if (
  !/^https?:\/\/(?:localhost|127\.0\.0\.1|\[::1\])(?::\d+)?\/?$/.test(base) ||
  base !== fixture.base
)
  throw new Error(
    "The load test must use the fixture’s local application origin.",
  );
export const options = {
  scenarios: {
    warmup: {
      executor: "shared-iterations",
      vus: 1,
      iterations: 3,
      exec: "readFlow",
    },
    measure: {
      executor: "constant-vus",
      vus: 2,
      duration: "20s",
      startTime: "3s",
      exec: "readFlow",
    },
  },
  thresholds: {
    "http_req_failed{scenario:measure}": ["rate==0"],
    "http_req_duration{scenario:measure}": ["p(95)<500"],
    checks: ["rate==1"],
  },
};
export function readFlow() {
  for (const days of [30, 180]) {
    const response = http.get(
      `${base}/api/projects/${fixture.projectId}/flow?days=${days}`,
      {
        headers: { Cookie: fixture.cookie },
        redirects: 0,
        tags: { name: `flow-${days}-days` },
      },
    );
    check(response, {
      "flow readable": (r) => r.status === 200,
      "window and data complete": (r) =>
        r.status === 200 &&
        r.json("days") === days &&
        r.json("tasksWithoutHistory") === 0 &&
        r.json("completedTasks") > 0,
    });
  }
  sleep(0.1);
}
