# PayFlow: Codex build kit (VS Code + ChatGPT Plus)

Use this file with `AGENTS.md`. Paste the prompts in order, one phase per new Codex chat. Commit to Git after every phase.

---

## A. Which Codex agent, model and settings to use

**Agent: Codex IDE extension in VS Code, running locally, in Agent mode (the mode that edits files and runs commands, with approvals on).**
Use the local agent rather than Codex cloud tasks for this project. PayFlow needs your local Docker for Postgres, Redis, Kafka and Testcontainers, and the local agent can run `docker compose` and `./mvnw verify` on your machine and fix what fails. Cloud tasks can't reach your Docker.

**Model: open the model picker in the Codex panel and use it like this.**

| Work | Model to pick | Reasoning effort |
|---|---|---|
| Phase 0 planning, ledger/payments phases (3–5), final review | The most capable model in your picker (OpenAI's docs currently list GPT-6 Astra as the top model and GPT-6.1 Sol as the near-Astra, cheaper option. Names change often, so trust your picker.) | High |
| Scaffolding, frontend, docs, CI, small fixes | The cheaper "near-top" model (e.g. GPT-6.1 Sol) | Medium |
| Tiny edits (rename, format, one-line fix) | Fastest/lightest model | Low or Medium |

Plus has usage limits. Use the top model only where correctness matters most (money, concurrency, reviews), and the cheaper one for everything else so you don't run out mid-build.

**Permissions:** keep the default approval mode (Codex asks before running commands or touching files outside the project). Approve `docker`, `./mvnw`, `npm`, `git status/diff` commands. Never approve commands that install Postgres, Redis or Kafka on your machine; everything runs in Docker.

**Plan mode:** for Phase 0 and any phase touching more than one service, switch on plan mode (`/plan` or Shift+Tab in the Codex panel) so it shows a plan before editing.

---

## B. One-time setup on your laptop (do this before Phase 0)

1. Install: **JDK 17** (Temurin), **Node.js 20 LTS**, **Git**, **Docker Desktop** (on Windows, enable the WSL 2 backend), **VS Code**.
2. Install the **Codex** extension in VS Code (publisher: OpenAI) and sign in with your ChatGPT Plus account.
3. Create the repo:
   ```bash
   mkdir payflow && cd payflow
   git init
   ```
4. Copy `AGENTS.md` into the `payflow/` root. Codex loads it automatically.
5. Open the `payflow` folder in VS Code and open the Codex panel.
6. Create an empty GitHub repo named `payflow` and connect it (`git remote add origin ...`). Push after every phase so your GitHub shows steady progress.

Make sure Docker Desktop is running before every session.

---

## C. The prompts (paste one per phase, in order)

> Tip: start each phase in a **new Codex chat** so it isn't carrying old context. AGENTS.md gives it everything it needs.

### Phase 0: Plan (top model, High, plan mode ON)

```
Read AGENTS.md fully. You are a senior payments engineer and my technical lead for this project.

Goal: produce the complete build plan for PayFlow before writing any code.

Deliver, as files:
1. docs/architecture.md: services, responsibilities, data ownership, a Mermaid component diagram, and a Mermaid sequence diagram for "send money" (gateway -> payments -> ledger -> outbox -> Kafka -> notifications), including the failure paths (insufficient funds, risk decline, duplicate request with same Idempotency-Key, ledger timeout).
2. docs/data-model.md: every table per service with columns, types, constraints, indexes (unique transfer_id, idempotency, outbox).
3. docs/api.md: every REST endpoint (method, path, auth, request/response JSON, error codes) and the versioned Kafka event schemas.
4. docs/decisions/: short ADRs for: minor-unit money, double-entry ledger, pessimistic locking with ordered locks, Redis idempotency keys, transactional outbox, database-per-service.
5. docs/plan.md: the phase-by-phase build order with acceptance criteria per phase.

Constraints: follow AGENTS.md exactly. Databases run only in Docker. Do not write application code yet.
Done when: all five docs exist, are consistent with each other, and you have listed any open questions for me.
```

Read the docs yourself. These are your interview prep notes. Ask Codex "explain this decision like I'm in an interview" for anything unclear.

### Phase 1: Scaffold + Docker infrastructure (cheaper model, Medium)

```
Read AGENTS.md and docs/plan.md.

Goal: scaffold the monorepo so every service starts and is healthy, with all databases running in Docker.

Build:
- Parent Maven POM (Java 17, Spring Boot 3.x), Maven wrapper, modules: common, services/gateway, accounts-service, ledger-service, payments-service, notification-service. Each service: Spring Boot app, Actuator health endpoint, springdoc Swagger UI, a /api/<service>/ping endpoint, Dockerfile (multi-stage, non-root user).
- infra/docker-compose.yml: postgres:16, redis:7, kafka (KRaft, no ZooKeeper), all services, frontend placeholder. Healthchecks on every container, depends_on with service_healthy, named volumes, profiles "infra" (databases only) and "all".
- infra/postgres/init.sql creating one database + user per service.
- .env.example with every variable; .gitignore covering .env, target/, node_modules/.
- common/: Money value object (long minor units + currency) with unit tests, ProblemDetail error model.
- Frontend placeholder: Vite + React + TypeScript + Tailwind showing "PayFlow".
- README.md: prerequisites (JDK 17, Node 20, Docker Desktop; no local databases), how to run.

Constraints: no database or Kafka installed on the host; everything in Docker. Compose runs in the foreground (no -d).
Done when: `./mvnw clean verify` passes, `docker compose -f infra/docker-compose.yml --profile all up --build` brings every container to healthy, and every /ping works through the gateway. Show me the commands you ran and their results.
```

### Phase 2: Accounts, auth and gateway security (cheaper model, Medium)

```
Read AGENTS.md, docs/api.md, docs/data-model.md.

Goal: users can register, log in and get a wallet; the gateway protects every other route.

Build:
- accounts-service: register (email, name, password with BCrypt), login returning a JWT access token, GET /me. Creating a user also creates a wallet in ledger-service (REST call) in INR. Flyway migrations.
- gateway: JWT validation filter, passes user id to downstream services in a header, rejects missing/invalid tokens with 401 ProblemDetail; Redis-backed rate limiting per user; CORS for the frontend origin.
- Bean Validation on all DTOs, consistent ProblemDetail errors with traceId.
- Tests: unit tests for auth logic; Testcontainers integration tests for register/login, duplicate email (409), wrong password (401), and gateway rejecting bad tokens.

Constraints: secrets from env vars only; never log passwords or tokens.
Done when: `./mvnw clean verify` passes and you show a curl session: register -> login -> GET /me through the gateway.
```

### Phase 3: Double-entry ledger (top model, High, plan mode ON)

```
Read AGENTS.md (section 4 is critical), docs/data-model.md, docs/decisions/.

Goal: implement ledger-service as the correct, concurrency-safe source of truth for money.

Build:
- Tables: ledger_accounts (wallets), ledger_entries (account_id, transfer_id, direction DEBIT/CREDIT, amount_minor, currency, created_at), transfers (transfer_id unique, status). Flyway migrations.
- POST /internal/transfers {transferId, fromAccount, toAccount, amount, currency}: in ONE transaction lock both wallet rows in ascending id order (PESSIMISTIC_WRITE), check sufficient balance, write exactly one DEBIT and one CREDIT, mark the transfer posted. Re-posting the same transferId returns the original result without new entries.
- POST /internal/accounts/{id}/topup for adding test funds (recorded as a transfer from a system "funding" account so the books still balance).
- GET balance and paginated entries for an account.

Tests (Testcontainers Postgres), all required:
- Happy path transfer; insufficient funds rejected and no entries written.
- Idempotency: same transferId twice -> one pair of entries.
- Invariant: after many random transfers, sum of all entries per currency == 0 and no user-wallet balance is negative (the system funding account is exempt).
- Concurrency: 50 parallel transfers from one wallet with balance for only 10 -> exactly 10 succeed, user-wallet balance never negative, no deadlock.
- Opposite-direction parallel transfers (A->B and B->A at the same time) complete without deadlock.

Done when: all tests pass, and you explain in plain language how locking order prevents deadlocks and how the invariant test proves correctness.
```

### Phase 4: Payments API, idempotency, risk rules, state machine (top model, High, plan mode ON)

```
Read AGENTS.md, docs/api.md, docs/architecture.md.

Goal: implement payments-service: the public "send money" API.

Build:
- POST /api/payments {toUserEmail, amount, currency, note} with required Idempotency-Key header.
  - Redis idempotency: same key + same request body hash -> return stored response; same key + different body -> 422; in-flight duplicate -> 409; keys expire after 24h.
  - Risk rules from config (Redis counters): max payments per user per minute, max amount per user per day, no self-transfer, amount > 0. Violations -> DECLINED with a reason code.
  - State machine CREATED -> COMPLETED | FAILED | DECLINED, every transition persisted with timestamp; illegal transitions throw.
  - Calls ledger-service with a generated transferId; handles ledger errors and timeouts (timeout -> remain CREATED, return 202 and reconcile using the same transferId; a timeout never proves failure).
- GET /api/payments (paginated history for the current user) and GET /api/payments/{id} (sender or recipient can read completed payments; only initiator sees other states).
- Transactional outbox: write payment.completed / payment.failed / payment.declined events (versioned schema from docs/api.md) to an outbox table in the same transaction; a scheduled publisher sends them to Kafka topic payments.events and marks them sent.

Tests (Testcontainers Postgres + Redis + Kafka):
- Retry storm: send the same request with the same Idempotency-Key 20 times in parallel -> exactly ONE payment and ONE ledger transfer.
- Different body with reused key -> 422.
- Each risk rule declines correctly.
- Outbox: event lands on Kafka after commit; no event if the transaction rolls back.
- Ownership: unrelated user C cannot read A-to-B payments (404); B can read completed received payments.

Done when: all tests pass and you give me a curl demo: register two users -> top up -> send money -> history -> duplicate request returns the same response.
```

### Phase 5: Notifications with retries and dead-letter topic (cheaper model, Medium)

```
Read AGENTS.md and docs/api.md.

Goal: notification-service consumes payments.events and stores notifications for sender and receiver.

Build: Spring Kafka consumer with idempotent processing (event id unique constraint), retry with backoff (3 attempts), then dead-letter to payments.events.DLT; GET /api/notifications for the current user (paginated, mark as read).
Tests (Testcontainers Kafka + Postgres): event -> notifications created; duplicate event -> no duplicates; poison message -> ends up on DLT.
Done when: tests pass and a payment made through the gateway produces notifications for both users.
```

### Phase 6: Frontend: professional fintech UI (cheaper model, Medium; top model if the design looks weak)

```
Read AGENTS.md and docs/api.md.

Goal: build a clean, professional, responsive PayFlow web app that looks like a real fintech product.

Design direction: modern fintech dashboard. Neutral background, one confident brand colour (deep blue), generous spacing, clear typography hierarchy, rounded cards, subtle shadows, accessible contrast (WCAG AA), loading skeletons, empty states, toast notifications, fully responsive from 360px phones to desktop, light and dark mode.

Pages:
- Login / Register with validated forms (React Hook Form + Zod).
- Dashboard: balance card, "Send money" and "Add test funds" actions, recent transactions list, unread notifications badge.
- Send Money: recipient email, amount, note, review-and-confirm step; generate an Idempotency-Key (UUID) per confirmed attempt and reuse it on retry; show clear declined/failed reasons.
- Transactions: paginated, filter by status, transaction detail with the status timeline.
- Notifications page.

Tech: typed API client in src/api with JWT handling, TanStack Query for server state, protected routes, format money from minor units with Intl.NumberFormat (never floats for maths).
Tests: Vitest + React Testing Library for the send-money form, money formatting and protected routing.
Docker: production build served by nginx, added to compose profile "all".
Done when: lint, tests and build pass, and the full flow works in the browser through the gateway. Describe the UI and list the screens.
```

### Phase 7: Load tests, CI and observability (cheaper model, Medium)

```
Read AGENTS.md.

Goal: measure performance and automate quality checks.

Build:
- load-tests/payments.js (k6): register users, top up, then ramp to sustained payment traffic; separate scenario that retries the same Idempotency-Key repeatedly. Thresholds: error rate < 1%, record p95 latency and requests/sec. Write a short results file load-tests/RESULTS.md with the numbers you measured on this machine.
- After the load test, run a ledger invariant check (sum of entries per currency == 0, no negative balances, duplicate-key scenario produced one payment per key) and record the result.
- .github/workflows/ci.yml: on push/PR run ./mvnw clean verify (Testcontainers works on GitHub runners) and frontend lint/test/build; cache Maven and npm.
- Actuator + Micrometer metrics: payment count by status, payment latency timer.
Done when: CI file is valid, k6 runs locally against docker compose, and RESULTS.md contains real measured numbers (do not invent numbers).
```

### Phase 8: Final review and polish (top model, High)

```
Act as a strict senior reviewer at a payments company. Review the entire PayFlow repo against AGENTS.md.

1. List every bug, race condition, money-handling risk, security issue, missing test and inconsistency between code and docs, ordered by severity.
2. Fix them one by one, running ./mvnw clean verify and the frontend checks after each fix.
3. Polish README.md for recruiters: one-paragraph pitch, architecture diagram (Mermaid), feature list, tech stack, how to run in 3 commands (databases in Docker), screenshots section, load-test results from load-tests/RESULTS.md, design decisions with links to ADRs.
Done when: every check passes and you give me a final summary of what was fixed.
```

---

## D. Interview prep prompt (run after Phase 8)

```
I will be interviewed about PayFlow for a software engineering internship at PayPal. Using this repo, give me:
1. A 60-second project pitch.
2. The 15 most likely interview questions about this project (idempotency, double-entry ledger, locking and deadlocks, outbox pattern, Kafka retries and DLT, JWT, rate limiting, scaling, failure handling), each with a clear answer that references our actual code (file and class names).
3. Three "what would you improve next" answers.
Quiz me one question at a time and correct my answers.
```

You must be able to explain every part yourself. That's what turns this into a strong interview, not just a strong resume.

---

## E. When you finish: send these back to me for the resume update

- GitHub repo link (public).
- From `load-tests/RESULTS.md`: p95 latency, requests/sec, error rate.
- Retry-storm result: number of duplicate requests sent and confirmation that only one payment was created per key.
- Concurrency test result (e.g., 50 parallel transfers, user-wallet balance never negative).
- Number of automated tests.

I'll turn these into "completed" bullets with your real numbers.

---

## F. If Codex gets stuck

- Paste the exact error and say: "Find the root cause, fix it, and re-run the failing command. Don't skip or delete tests."
- If it drifts from the design: "Re-read AGENTS.md section X and redo this to match it."
- If a phase gets too big: "Stop. Split the remaining work into smaller steps and do only step 1."
- Use `/review` in Codex on your uncommitted changes before every commit.