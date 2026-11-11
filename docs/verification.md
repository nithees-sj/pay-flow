# Verification evidence

Final verification: **11 November 2026**. Results are from executed commands, not estimated or inferred passes. All approved Phases 0–8 have implementation and verification evidence below. Phase 0 documents were completed before application implementation began.

## Java

Environment: project-local ignored JDK 17, Maven wrapper 3.9.11, Docker Desktop Linux. The host default JDK differs, so the run explicitly selected JDK 17:

```sh
export JAVA_HOME="$PWD/.tools/jdk17"
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST="$(docker context inspect --format '{{.Endpoints.docker.Host}}')"
export DOCKER_API_VERSION=1.44
./mvnw -B -ntp clean verify
```

**BUILD SUCCESS** at 11:50:31 IST; 2 minutes 25 seconds. All seven reactor projects passed. 42 tests, zero failures, errors or skipped tests:

| Suite | Tests | Evidence covered |
|---|---:|---|
| MoneyTest | 5 | Exact arithmetic, parsing, currency and overflow |
| AccountsServiceTest | 3 | Normalization, credential behavior and BCrypt byte limit |
| GatewayIT | 3 | JWT rejection/validation, internal route protection, Redis limiting |
| AccountsIT | 3 | Register/login/me, duplicates, credentials, provisioning recovery, validation/identity |
| LedgerIT | 9 | Transfer pairing, insufficient funds, idempotency, concurrency, ordered locks, random invariants, overflow and immutability |
| PaymentsIT | 14 | 20 parallel retries, mismatch422, timeout recovery, risk rules, Redis loss, expiry, ownership, outbox rollback/publication, state guard, cache-after-commit and response crash recovery |
| NotificationsIT | 5 | Both participants, duplicate events, ownership/read state, transaction rollback, poison-message DLT |

Integration tests use Testcontainers PostgreSQL 16, Redis 7 and Kafka KRaft. Expected duplicate-key/poison-message errors in test logs are exercised failure paths; they are not test failures. Earlier phase-specific builds also passed and were followed by this complete reactor run after the backend review fixes.

## Frontend

```sh
cd frontend
npm run lint
npm test
npm run build
npm audit --audit-level=moderate
```

PASS after the dependency security updates: six test files, **21 tests** (money precision, send form, protected routing, retry recovery, top-up terminal/unknown outcomes and initial scaffold). TypeScript strict checking and Vite production build passed. Audit: **0 vulnerabilities**. React 18 remains in use; patched React Router 7, Zod 4/resolvers, Vitest 4.1.x and a narrow TypeScript ESLint minimatch override remove the reported advisories. A harmless upstream Zod pure-annotation warning is stripped by Rollup; the build completes.

Browser walkthrough against nginx/gateway: registration and login, add ₹5,000 test funds, send ₹125.50, inspect history/timeline, receive notifications, mark read. Final mobile walkthrough sent another ₹12.50 and confirmed COMPLETED. Checked responsive layout at 360×800: document content width equaled viewport width (345 CSS px after scrollbar), closed navigation was hidden, menu links worked. Checked light/dark and captured [screenshots](screenshots/). Authentication and navigation were rechecked after rebuilding the patched frontend.

## Docker and live API

```sh
docker compose -f infra/docker-compose.yml --profile all config --quiet
docker compose -f infra/docker-compose.yml --profile all up --build
node scripts/smoke.mjs
```

Compose configuration passed. Foreground Compose built the application images; PostgreSQL, Redis, Kafka, gateway, accounts, ledger, payments, notifications and frontend all reached healthy. Kafka's image initializes named-volume ownership for its nonroot runtime; the frontend health probe uses explicit IPv4. One Docker registry request timed out during a frontend rebuild; retry succeeded. No host databases were installed and no volumes were reset.

The smoke script invokes real curl requests through the gateway and performs an independent ledger query for the retry storm. Final run at 06:25:06 UTC:

```text
register/login Demo Sender: 201 ACTIVE / 200 (token redacted)
register/login Demo Receiver: 201 ACTIVE / 200 (token redacted)
register/login Unrelated User: 201 ACTIVE / 200 (token redacted)
GET /api/me and spoofed identity protection: PASS
POST /api/wallet/topups: 201 COMPLETED
Send / replay / changed-body422 / recipient access / outsider404 / balances: PASS
20 concurrent curl retries: ONE payment, ONE transfer, TWO ledger entries
Kafka notifications for both participants: PASS
```

All five authenticated `/ping` routes passed. Machine-readable evidence: [smoke-results.json](smoke-results.json). Credentials/tokens are intentionally omitted from stored evidence.

## Load, documentation and CI

```sh
k6 run load-tests/payments.js
node scripts/check-invariants.mjs
node scripts/check-docs.mjs
```

k6 exited 0: 975 HTTP requests, 933 sustained completed payments, 0% unexpected errors, **127.24 ms payment p95**, 17.05 total HTTP requests/second including setup/teardown. The 20-request duplicate scenario produced one payment and exactly two ledger entries. Post-load signed entry sum was zero; all overdraft, pair, cached balance, outbox and duplicate-scope checks passed. [Measured results and raw artifacts](../load-tests/RESULTS.md) explain raised load-only limits, hardware, timing and scope. Normal gateway/risk limits were restored afterward and checked inside the containers: 120 gateway requests/minute, 10 payment attempts/minute and 10000000 paise/day. Final Prometheus output included payment count/latency and an outbox backlog of zero. The infra-only profile resolves to exactly Kafka, PostgreSQL and Redis.

Documentation inventory, six ADRs and JSON examples passed validation. Both architecture Mermaid blocks were actually rendered in a local browser; a sequence-label punctuation error was corrected and both then rendered without syntax errors. README uses the same component diagram. Local Markdown links are checked for missing files.

GitHub Actions YAML parsed successfully with push/PR triggers and backend/frontend/documentation jobs. Compose configuration also validated. **No hosted GitHub Actions run is claimed**: this checkout has no Git repository/remote. Workflow execution will occur after it is pushed to GitHub.

## Completion and remaining scope

[Review findings and fixes](review.md) record correctness, security, UI and documentation corrections. No blocking open questions remain. Tests and the measured run provide evidence for the tested workloads; they do not prove production capacity, availability or correctness under every possible failure. Production deployment, long soak/chaos testing, real payment rails and RiskLens are outside the approved V1 scope.
