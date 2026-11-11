# PayFlow build plan

Approved scope: implement Phases 0–8 sequentially with real verification. Backend feature packages use api/domain/infra; frontend uses src/api, src/app, src/components and src/features. No host databases, no application code in Phase 0, no invented test/load results. This plan preserves the approved decisions in AGENTS.md section 11.

| Phase | Deliverables | Acceptance gate |
|---|---|---|
| 0 | Architecture/diagrams, data model, REST/events, six ADRs, this plan | Files agree; JSON examples parse; diagrams render; open questions listed |
| 1 | Java 17 Maven modules/wrapper; common Money/problems; service ping/health/OpenAPI; multi-stage nonroot Dockerfiles; Compose profiles/health/volumes; frontend placeholder; env and README | mvnw clean verify; frontend lint/test/build; all Compose containers healthy; gateway pings |
| 2 | Accounts BCrypt/JWT, registration/login/me, wallet provisioning/recovery, minimal ledger account PUT, gateway CORS/auth/rate-limit, service auth | Postgres/Redis Testcontainers; duplicate email409; bad credentials401; spoofed identity ignored; register/login/me curl |
| 3 | Ledger transfers/entries/cached balances, immutable outcomes, ordered locks, internal test funding | Postgres Testcontainers: duplicate/conflicting ID, insufficient funds zero entries, 50 concurrent/10 funded exactly10, opposite-direction no deadlock, random invariants, overflow |
| 4 | Payments durable idempotency/Redis risk, transitions, reconciliation, participant history, test topups, outbox/Kafka | 20-request storm one payment/transfer; changed body422; all risk reasons; unknown timeout recovery; Redis/process crash cases; outbox only after commit; outsider404 |
| 5 | Notifications consumer, receipts, retries/DLT, list/read | Kafka+Postgres tests: both participants, duplicate no effects, poison DLT, restart safety; end-to-end payment notification |
| 6 | Responsive fintech dashboard, auth, send/review, test-funding, history/detail timeline, notifications, themes | strict TS, lint/Vitest/build; money precision; protected routes; reuse key on retry; browser walkthrough at360px and desktop |
| 7 | k6 sustained/retry scenarios, CI, Actuator/Micrometer metrics, measured RESULTS.md | <1% unexpected errors; real p95/rps; post-load ledger and dedup invariants; valid workflow |
| 8 | Strict correctness/security review/fixes, recruiter README/screenshots/decisions/results | all required checks pass; review findings resolved or explicitly recorded; no fabricated completion |

## Execution details

Use JDK17 locally (a project-local ignored JDK is acceptable); Maven wrapper pins Maven; frontend lockfile pins dependencies. Version family: Spring Boot3.5, Cloud2025.0, springdoc2.8. PostgreSQL16, Redis7, Kafka KRaft are containers only. Compose runs foreground, never -d. Stop with Ctrl+C then docker compose -f infra/docker-compose.yml down. Never reset volumes without explicit request.

Phase2 includes ledger wallet creation because registration depends on it. Phase3 adds financial operations. Service/management endpoints are loopback-only locally; public APIs route through gateway. Tests use Docker Testcontainers, not local daemons. Verify per phase and run complete suites at final gate. Do not disable failing tests.

Risk limits default to 10 attempts/minute and 10000000 paise/day (UTC). Funding cap 1000000 paise/request and explicit demo flag. Auth tokens expire after15 minutes, memory-only browser storage. Currency INR only. No FX, real payment rails, refunds, refresh tokens, external notification provider, or RiskLens implementation.

## Open questions

None blocking. The user approved timeout reconciliation, negative system funding exception, durable idempotency, decimal-string wire money, demo-only funding, and received-payment visibility. Operational limits are configuration, not unresolved architecture.

## Verification log

Execution evidence and exact commands are maintained in docs/verification.md. Phase0 validation is documentation-only; build/tests begin once Phase1 exists.
