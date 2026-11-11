# AGENTS.md — PayFlow

Codex: read this file fully before every task. It is the source of truth for architecture, conventions and the definition of done. If a request conflicts with this file, stop and ask.

## 1. What PayFlow is

A production-style distributed payments and wallet platform (think "mini PayPal") built as a portfolio project for a software engineering internship. Users register, get a wallet, add test funds, send money to other users, and see their transaction history. Every money movement is recorded in a double-entry ledger, every payment API is idempotent, and payment events flow through Kafka.

Quality bar: code a senior payments engineer would approve in review. Clear boundaries, correct money handling, real tests, readable code, no shortcuts that would lose or duplicate money.

## 2. Architecture

Monorepo, Maven multi-module for Java, separate frontend.

```
payflow/
  AGENTS.md
  README.md
  pom.xml                      # parent POM (Java 17, Spring Boot 3.x)
  common/                      # shared DTOs, error model, Money type, event schemas
  services/
    gateway/                   # Spring Cloud Gateway: routing, JWT validation, Redis rate limiting, CORS
    accounts-service/          # users, registration/login (issues JWT), wallets
    ledger-service/            # double-entry ledger, balances, transfers (source of truth for money)
    payments-service/          # payment API, idempotency, risk checks, state machine, outbox -> Kafka
    notification-service/      # Kafka consumer, stores user notifications, retries + dead-letter topic
  frontend/                    # React + Vite + TypeScript + Tailwind
  infra/
    docker-compose.yml         # postgres, redis, kafka (KRaft), all services, frontend
    postgres/init.sql          # one database per service
  load-tests/                  # k6 scripts
  docs/                        # architecture.md, api.md, decisions/ (ADRs), diagrams (Mermaid)
  .github/workflows/ci.yml
```

Request flow for a payment:
1. Frontend -> gateway (`POST /api/payments`, JWT, `Idempotency-Key` header).
2. Gateway validates JWT, applies rate limit, forwards to payments-service.
3. payments-service: checks Redis idempotency key -> runs velocity risk rules -> creates payment (state CREATED) -> calls ledger-service to post the transfer -> marks COMPLETED / FAILED / DECLINED -> writes `payment.completed` (or failed) event to an outbox table in the same DB transaction.
4. Outbox publisher sends events to Kafka topic `payments.events`.
5. notification-service consumes and stores a notification; failures retry, then go to `payments.events.DLT`.

Service ownership: each service owns its own Postgres database. No service reads another service's tables. Cross-service calls go over REST (synchronous) or Kafka (asynchronous) only.

Future integration: a separate Python service "RiskLens" will consume `payments.events` for ML fraud scoring. Keep the event schema versioned and documented in `docs/api.md`.

## 3. Tech stack (do not substitute without asking)

- Java 17, Spring Boot 3.x, Spring Web, Spring Data JPA, Spring Security, Spring Cloud Gateway, Spring Kafka, Spring Data Redis
- PostgreSQL 16, Flyway migrations (never `ddl-auto=update`)
- Redis 7 (idempotency keys, rate limiting, velocity counters)
- Apache Kafka (KRaft mode, no ZooKeeper)
- springdoc-openapi (Swagger UI per service), Spring Boot Actuator + Micrometer
- Tests: JUnit 5, Mockito, AssertJ, Testcontainers (Postgres, Redis, Kafka), Spring Boot Test
- Frontend: React 18, Vite, TypeScript (strict), Tailwind CSS, React Router, TanStack Query, React Hook Form + Zod, Vitest + React Testing Library
- Load testing: k6
- Build: Maven wrapper (`./mvnw`), npm
- CI: GitHub Actions

## 4. Money and correctness rules (non-negotiable)

- Store amounts as `long` minor units (paise/cents) plus ISO-4217 currency code. Never `double` or `float`. Use a `Money` value object in `common/`.
- Ledger is double-entry: every transfer writes exactly one debit and one credit entry of equal amount in the same DB transaction. The sum of all entries per currency is always zero.
- Balances are derived from ledger entries (a cached balance column is allowed only if updated in the same transaction and covered by tests).
- Prevent overdraft and race conditions with row locking (`SELECT ... FOR UPDATE` / `@Lock(PESSIMISTIC_WRITE)`) on the wallet rows, locking in a consistent order (lower account id first) to avoid deadlocks.
- Every transfer has a unique `transfer_id`; a DB unique constraint makes ledger posting idempotent.
- `POST /payments` requires an `Idempotency-Key` header. Same key + same body returns the original response; same key + different body returns 422. Keys expire after 24h.
- Payment state machine: CREATED -> COMPLETED | FAILED | DECLINED. Illegal transitions throw. Persist every transition with a timestamp.
- Events are published via the transactional outbox pattern, never directly inside the business transaction.
- Risk rules (velocity checks) in payments-service: max payments per user per minute, max amount per user per day, block self-transfers, block amounts <= 0. Limits come from config.

## 5. Security rules

- Passwords hashed with BCrypt. JWT (access token, short expiry) signed with a secret from env vars.
- Secrets only via environment variables / `.env` (git-ignored). Provide `.env.example`. Never commit real secrets.
- Validate all input (Bean Validation on DTOs). Return consistent error JSON: `{ "code", "message", "traceId", "details" }` using RFC 7807-style problem details.
- Users can only read their own wallet, payments and notifications. Enforce in the service, not only in the UI.
- Never log full tokens, passwords or full card-like numbers.

## 6. Code conventions

- Package by feature: `com.payflow.<service>.<feature>` with `api` (controllers, DTOs), `domain` (entities, services), `infra` (repositories, clients, Kafka).
- Controllers are thin. Business logic lives in services. Entities are not returned from controllers; map to DTOs.
- Constructor injection only. Prefer records for DTOs and events.
- Meaningful names, small methods, Javadoc on public service methods that touch money.
- Frontend: feature folders, typed API client in `src/api`, no `any`, components small and reusable.

## 7. Infrastructure rule: databases run in Docker only

- PostgreSQL, Redis and Kafka run ONLY as Docker containers defined in `infra/docker-compose.yml`. Never install them on the host machine, never run them as host system services/daemons (no `apt install postgresql`, `brew services`, Windows services, `systemctl`), and never tell the developer to do so.
- Data persists in named Docker volumes (`pgdata`, `kafkadata`). Reset everything with `docker compose -f infra/docker-compose.yml down -v`.
- Every infra container has a healthcheck; services use `depends_on: condition: service_healthy`.
- Run compose in the foreground (no `-d`) so logs are visible; stop with `Ctrl+C`, then `docker compose -f infra/docker-compose.yml down`.
- Provide an infra-only profile so the developer can run just the databases in Docker and start a Java service from the IDE: `docker compose -f infra/docker-compose.yml --profile infra up`.
- Integration tests use Testcontainers (also Docker); they must not depend on anything installed on the host.
- Host only needs: JDK 17, Node 20+, Git, Docker Desktop (Docker Desktop itself running is fine).

## 8. Commands

- Start only databases/infra (Postgres, Redis, Kafka) in Docker: `docker compose -f infra/docker-compose.yml --profile infra up`
- Start infrastructure + all services: `docker compose -f infra/docker-compose.yml --profile all up --build`
- Build and test all Java: `./mvnw clean verify`
- Test one service: `./mvnw -pl services/payments-service -am verify`
- Frontend: `cd frontend && npm install && npm run dev` / `npm run lint` / `npm test` / `npm run build`
- Load test: `k6 run load-tests/payments.js`

Keep these commands working. If you change how something runs, update this section and README.md.

## 9. Definition of done (for every task)

A task is done only when:
1. Code compiles and `./mvnw clean verify` passes (and frontend lint/test/build pass if frontend changed).
2. New behaviour has tests: unit tests for logic, Testcontainers integration tests for DB/Redis/Kafka behaviour.
3. Flyway migrations added for any schema change.
4. OpenAPI annotations and `docs/api.md` updated for any API change.
5. README / docs updated if setup, commands or architecture changed.
6. You summarize: what changed, files touched, how to run it, and how you verified it.

## 10. Working style

- For any task touching more than one service, write a short plan first and wait for approval.
- Work in small, reviewable steps. Do not rewrite unrelated code.
- If a test fails, fix the root cause; never delete or skip tests to make the build green.
- If something is ambiguous, ask instead of guessing.
- After finishing, explain the key design decisions in plain language so the developer can explain them in an interview.

## 11. Approved Phase 0 clarifications

- Ledger timeout is an unknown outcome: keep CREATED, return 202 and reconcile using the same transfer ID. Never mark FAILED solely because of a timeout.
- Only user wallets must remain nonnegative. The single system funding account per currency carries the balancing negative amount for demo funding.
- Redis idempotency is backed by a durable payments-database reservation and original response. The 24-hour window does not slide on retry.
- Money on REST/Kafka is a decimal minor-unit string; Java/Postgres use long/BIGINT. V1 currency is INR.
- Both participants can read completed payments; only the initiator sees pending, failed or declined attempts. An unrelated third user gets 404.
- Self-service test funding is enabled only by explicit demo configuration, for the authenticated user's own wallet.
- Accounts owns wallet identity/provisioning metadata; ledger owns financial accounts, balances and entries. Gateway is stateless and has no business tables.
- Minimal ledger account provisioning is implemented in Phase 2; transfers follow in Phase 3.
- The approved whole-project plan authorizes progression through Phases 0–8, with verification gates and progress reports; do not ask for repeated phase approval.
