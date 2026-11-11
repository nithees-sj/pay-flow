import http from "k6/http";
import { check, sleep, fail } from "k6";
import { Rate, Trend, Counter } from "k6/metrics";
import exec from "k6/execution";

const base = __ENV.BASE_URL || "http://localhost:8080";
const unexpectedErrors = new Rate("unexpected_errors");
const paymentLatency = new Trend("payment_latency", true);
const terminalPayments = new Counter("terminal_payments");
const duplicateResponses = new Counter("duplicate_responses");
http.setResponseCallback(http.expectedStatuses({ min: 200, max: 299 }, 409));
export const options = {
  scenarios: {
    sustained: {
      executor: "ramping-vus",
      exec: "paymentTraffic",
      startVUs: 0,
      stages: [
        { duration: "10s", target: 5 },
        { duration: "25s", target: 5 },
        { duration: "10s", target: 0 },
      ],
      gracefulRampDown: "10s",
    },
    duplicates: {
      executor: "per-vu-iterations",
      exec: "retryStorm",
      vus: 1,
      iterations: 1,
      startTime: "5s",
      maxDuration: "30s",
    },
  },
  thresholds: {
    unexpected_errors: ["rate<0.01"],
    http_req_failed: ["rate<0.01"],
    checks: ["rate>0.99"],
  },
  batch: 20,
  batchPerHost: 20,
  summaryTrendStats: ["avg", "min", "med", "max", "p(90)", "p(95)", "p(99)"],
};
const password = "load-test-only-password-2026";
function params(token, key) {
  return {
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(key ? { "Idempotency-Key": key } : {}),
    },
    tags: { name: "POST /api/payments" },
  };
}
function json(response) {
  try {
    return response.json();
  } catch {
    return null;
  }
}
export function setup() {
  const users = [];
  const run = Date.now().toString(36);
  for (let i = 0; i < 7; i++) {
    const email = `load.${run}.${i}@example.test`;
    const registered = http.post(
      `${base}/api/auth/register`,
      JSON.stringify({ name: `Load User ${i}`, email, password }),
      { ...params(), tags: { name: "register" } },
    );
    if (registered.status !== 201)
      fail(`Registration returned ${registered.status}`);
    const profile = json(registered);
    const login = http.post(
      `${base}/api/auth/login`,
      JSON.stringify({ email, password }),
      { ...params(), tags: { name: "login" } },
    );
    if (login.status !== 200) fail(`Login returned ${login.status}`);
    const token = json(login).accessToken;
    if (i < 6) {
      const topup = http.post(
        `${base}/api/wallet/topups`,
        JSON.stringify({ amount: "1000000", currency: "INR" }),
        { ...params(token, `fund-${run}-${i}`), tags: { name: "topup" } },
      );
      if (topup.status !== 201 || json(topup)?.status !== "COMPLETED")
        fail(`Funding returned ${topup.status}`);
    }
    users.push({ email, token, id: profile.user.id });
  }
  return { users, run, retryKey: `retry-${run}` };
}
export function paymentTraffic(data) {
  const index = (exec.vu.idInTest - 1) % 5;
  const user = data.users[index];
  const recipient = data.users[(index + 1) % 5];
  const response = http.post(
    `${base}/api/payments`,
    JSON.stringify({
      toUserEmail: recipient.email,
      amount: "100",
      currency: "INR",
      note: "k6 sustained payment",
    }),
    params(
      user.token,
      `pay-${data.run}-${exec.vu.idInTest}-${exec.scenario.iterationInTest}`,
    ),
  );
  paymentLatency.add(response.timings.duration);
  const body = json(response);
  const accepted = response.status === 201 && body?.status === "COMPLETED";
  unexpectedErrors.add(!accepted);
  check(response, { "new payment completes": () => accepted });
  if (accepted) terminalPayments.add(1);
  sleep(0.1);
}
export function retryStorm(data) {
  const body = JSON.stringify({
    toUserEmail: data.users[6].email,
    amount: "100",
    currency: "INR",
    note: "k6 duplicate storm",
  });
  const requests = Array.from({ length: 20 }, () => ({
    method: "POST",
    url: `${base}/api/payments`,
    body,
    params: params(data.users[5].token, data.retryKey),
  }));
  const responses = http.batch(requests);
  const ids = new Set();
  for (const response of responses) {
    duplicateResponses.add(1);
    const accepted =
      response.status === 201 ||
      response.status === 202 ||
      response.status === 409;
    unexpectedErrors.add(!accepted);
    check(response, {
      "duplicate is replay or in-flight conflict": () => accepted,
    });
    const value = json(response);
    if (value?.id) ids.add(value.id);
  }
  sleep(0.5);
  const replay = http.post(
    `${base}/api/payments`,
    body,
    params(data.users[5].token, data.retryKey),
  );
  const value = json(replay);
  if (value?.id) ids.add(value.id);
  check(replay, {
    "retry returns exactly one payment ID": () =>
      ids.size === 1 && replay.status === 201 && value?.status === "COMPLETED",
  });
}
export function teardown(data) {
  const response = http.get(`${base}/api/payments?kind=PAYMENT&size=100`, {
    headers: { Authorization: `Bearer ${data.users[5].token}` },
    tags: { name: "verify duplicate history" },
  });
  const history = json(response);
  check(response, {
    "duplicate storm creates exactly one payment": () =>
      response.status === 200 && history?.totalElements === 1,
  });
  console.log(
    "PAYFLOW_RETRY_RESULT " +
      JSON.stringify({
        run: data.run,
        requests: 20,
        payments: history?.totalElements,
        paymentId: history?.items?.[0]?.id,
        transferId: history?.items?.[0]?.transferId,
      }),
  );
}
export function handleSummary(data) {
  return {
    "load-tests/summary.json": JSON.stringify(data, null, 2),
    stdout: `\nPayFlow measured run: ${data.metrics.http_reqs.values.count} HTTP requests; payment p95 ${data.metrics.payment_latency.values["p(95)"].toFixed(2)} ms; unexpected errors ${(data.metrics.unexpected_errors.values.rate * 100).toFixed(2)}%.\n`,
  };
}
