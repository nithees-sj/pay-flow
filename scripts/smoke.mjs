import { spawn, execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import { writeFileSync } from "node:fs";
import assert from "node:assert/strict";
const base = process.env.BASE_URL ?? "http://localhost:8080";
// curl receives credentials through stdin, never command arguments or console output.
function request(method, path, body, token, key, extra = {}) {
  const config = [
    `url = ${JSON.stringify(base + path)}`,
    `request = ${JSON.stringify(method)}`,
    'header = "Content-Type: application/json"',
  ];
  if (token)
    config.push(`header = ${JSON.stringify("Authorization: Bearer " + token)}`);
  if (key) config.push(`header = ${JSON.stringify("Idempotency-Key: " + key)}`);
  for (const [name, value] of Object.entries(extra))
    config.push(`header = ${JSON.stringify(name + ": " + value)}`);
  if (body !== undefined)
    config.push(`data = ${JSON.stringify(JSON.stringify(body))}`);
  return new Promise((resolve, reject) => {
    const child = spawn("curl", [
      "--silent",
      "--show-error",
      "--max-time",
      "15",
      "--config",
      "-",
      "--write-out",
      "\n%{http_code}",
    ]);
    let out = "",
      err = "";
    child.stdout.on("data", (v) => (out += v));
    child.stderr.on("data", (v) => (err += v));
    child.on("error", reject);
    child.on("close", (code) => {
      if (code !== 0) return reject(new Error(`curl failed (${code}): ${err}`));
      const split = out.lastIndexOf("\n");
      try {
        resolve({
          status: Number(out.slice(split + 1)),
          body: JSON.parse(out.slice(0, split)),
        });
      } catch {
        reject(new Error("Non-JSON API response"));
      }
    });
    child.stdin.end(config.join("\n") + "\n");
  });
}
const run = randomUUID().slice(0, 8);
const password = "curl-demo-only-password-2026";
const users = [];
for (const [i, name] of [
  "Demo Sender",
  "Demo Receiver",
  "Unrelated User",
].entries()) {
  const email = `smoke.${run}.${i}@example.test`;
  const registered = await request("POST", "/api/auth/register", {
    name,
    email,
    password,
  });
  assert.equal(registered.status, 201);
  assert.equal(registered.body.wallet.status, "ACTIVE");
  const login = await request("POST", "/api/auth/login", { email, password });
  assert.equal(login.status, 200);
  users.push({
    email,
    id: registered.body.user.id,
    token: login.body.accessToken,
  });
  console.log(`register/login ${name}: 201 ACTIVE / 200 (token redacted)`);
}
const [sender, receiver, outsider] = users;
const me = await request("GET", "/api/me", undefined, sender.token, undefined, {
  "X-User-Id": outsider.id,
});
assert.equal(me.status, 200);
assert.equal(me.body.user.id, sender.id);
console.log("GET /api/me and spoofed identity protection: PASS");
for (const service of [
  "gateway",
  "accounts",
  "ledger",
  "payments",
  "notifications",
])
  assert.equal(
    (await request("GET", `/api/${service}/ping`, undefined, sender.token))
      .status,
    200,
  );
assert.equal((await request("GET", "/api/me")).status, 401);
const funded = await request(
  "POST",
  "/api/wallet/topups",
  { amount: "250000", currency: "INR" },
  sender.token,
  randomUUID(),
);
assert.equal(funded.status, 201);
assert.equal(funded.body.status, "COMPLETED");
console.log("POST /api/wallet/topups: 201 COMPLETED");
const intent = {
  toUserEmail: receiver.email,
  amount: "12345",
  currency: "INR",
  note: "Shared lunch",
};
const key = randomUUID();
const first = await request("POST", "/api/payments", intent, sender.token, key);
assert.equal(first.status, 201);
assert.equal(first.body.status, "COMPLETED");
const replay = await request(
  "POST",
  "/api/payments",
  intent,
  sender.token,
  key,
);
assert.deepEqual(replay, first);
assert.equal(
  (
    await request(
      "POST",
      "/api/payments",
      { ...intent, amount: "12346" },
      sender.token,
      key,
    )
  ).status,
  422,
);
assert.equal(
  (
    await request(
      "GET",
      `/api/payments/${first.body.id}`,
      undefined,
      receiver.token,
    )
  ).status,
  200,
);
assert.equal(
  (
    await request(
      "GET",
      `/api/payments/${first.body.id}`,
      undefined,
      outsider.token,
    )
  ).status,
  404,
);
assert.equal(
  (await request("GET", "/api/wallet", undefined, sender.token)).body.balance,
  "237655",
);
assert.equal(
  (await request("GET", "/api/wallet", undefined, receiver.token)).body.balance,
  "12345",
);
console.log(
  "Send / replay / changed-body422 / recipient access / outsider404 / balances: PASS",
);
const stormKey = randomUUID();
const stormBody = {
  ...intent,
  amount: "100",
  note: "20 concurrent curl requests",
};
const storm = await Promise.all(
  Array.from({ length: 20 }, () =>
    request("POST", "/api/payments", stormBody, sender.token, stormKey),
  ),
);
assert.ok(storm.every((r) => [201, 202, 409].includes(r.status)));
const settled = await request(
  "POST",
  "/api/payments",
  stormBody,
  sender.token,
  stormKey,
);
assert.equal(settled.body.status, "COMPLETED");
const ids = new Set(storm.filter((r) => r.body.id).map((r) => r.body.id));
ids.add(settled.body.id);
assert.equal(ids.size, 1);
const history = await request(
  "GET",
  "/api/payments?kind=PAYMENT",
  undefined,
  sender.token,
);
assert.equal(history.body.totalElements, 2);
const transferId = settled.body.transferId;
assert.match(transferId, /^[0-9a-f-]{36}$/);
const entryCount = Number(
  execFileSync(
    "docker",
    [
      "compose",
      "-f",
      "infra/docker-compose.yml",
      "exec",
      "-T",
      "postgres",
      "psql",
      "-U",
      "postgres",
      "-d",
      "ledger_db",
      "-At",
      "-c",
      `SELECT count(*) FROM ledger_entries WHERE transfer_id='${transferId}'::uuid`,
    ],
    { encoding: "utf8" },
  ).trim(),
);
assert.equal(entryCount, 2);
console.log(
  "20 concurrent curl retries: ONE payment, ONE transfer, TWO ledger entries",
);
let notifications = false;
for (let i = 0; i < 30; i++) {
  const a = await request("GET", "/api/notifications", undefined, sender.token),
    b = await request("GET", "/api/notifications", undefined, receiver.token);
  if (
    a.body.items.some((n) => n.paymentId === first.body.id) &&
    b.body.items.some((n) => n.paymentId === first.body.id)
  ) {
    notifications = true;
    break;
  }
  await new Promise((resolve) => setTimeout(resolve, 500));
}
assert.ok(notifications);
console.log("Kafka notifications for both participants: PASS");
writeFileSync(
  "docs/smoke-results.json",
  JSON.stringify(
    {
      checkedAt: new Date().toISOString(),
      passed: true,
      parallelRetries: 20,
      uniqueStormPaymentIds: ids.size,
      stormLedgerEntries: entryCount,
      notificationsForBothParticipants: true,
      authenticatedPingRoutes: 5,
    },
    null,
    2,
  ) + "\n",
);
