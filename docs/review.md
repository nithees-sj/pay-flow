# Final engineering review

Reviewed against AGENTS.md and the approved Phase 0 clarifications. Findings below are ordered by severity. All listed implementation findings are resolved; residual scope limits are recorded separately. This is a code and behavior review, not a claim that all possible defects have been proved absent.

## Resolved findings

| Severity | Finding and risk | Resolution and evidence |
|---|---|---|
| Critical | Treating ledger timeout as failure can invite a second debit while the first transfer actually committed. | Approved design keeps CREATED, returns 202, and reconciles the permanent transfer ID. Integration test simulates a lost response after ledger posting and verifies one transfer. |
| High | Redis-only idempotency would lose payment uniqueness after cache loss. | Durable unique PostgreSQL reservation, canonical request hash, original response, fixed expiry; Redis-loss and 20-request retry tests. |
| High | Caching an original HTTP response before its transaction commits can serve data that rolled back. | Cache population moved to after-commit callbacks. Explicit rollback regression test verifies no cache response survives. |
| High | Crash after terminal payment commit but before response persistence could strand retries at 409 forever. | Retry reconstructs the response from the immutable terminal payment. Dedicated regression test covers the crash window. |
| High | Unordered wallet locks and unchecked integer arithmetic risk deadlock, overdraft or overflow. | Ascending account locks, checked Money arithmetic, durable transfer identity and deferred pair constraints. Tests cover 50 concurrent withdrawals with exactly 10 successes, opposite-direction traffic, overflow, immutable entries and random conservation checks. |
| High | Lost risk counters could reset velocity controls. | Per-user serialized database attempt history seeds Redis atomically; durable risk decisions avoid retry counting. Redis-loss and each risk-rule tests pass. |
| High | Dependency audit reported critical test-tool and high validation-library advisories. | Updated Vitest to patched 4.1.x, Zod 4 and compatible resolvers; retained React 18 and Vite 6. Updated React Router and applied a narrow minimatch override for TypeScript ESLint. Audit now reports zero vulnerabilities; frontend lint, all 21 tests and build pass. |
| Medium | Shared internal credentials were supplied to services that did not need them; blank optional credentials needed explicit rejection. | Compose supplies caller tokens only where used. Internal authentication rejects blank expected secrets and clients reject blank outbound credentials. Service auth remains separate from untrusted identity headers. Full suite and live smoke pass. |
| Medium | Concurrent provisioning workers could repeatedly claim the same wallet. | Atomic due-work claim with a lease and SKIP LOCKED; provisioning remains idempotent and retries recover after lease expiry. Registration/provisioning recovery tests pass. |
| Medium | An uncertain client retry could accidentally generate a new payment key. | Persist unresolved attempt metadata, reuse the original key/body, and poll the existing payment after 202. Tests cover lost responses, navigation recovery and pending-result polling. |
| Medium | A definitive failed top-up left its old key attached to the form. | Clear the attempt only for a terminal result; preserve it after a network error. Both cases have focused frontend tests. |
| Medium | Hidden mobile navigation remained focusable, and a table accessibility header caused horizontal overflow. | Hide the closed sidebar from layout/accessibility and contain the table header. Browser verified 360 px layout with equal content/viewport width and functioning menu. Improved light/dark contrast. |
| Low | Balance fetch errors could look like a zero balance on the send page. | Render an unavailable-balance error rather than substituting zero. |
| Low | Kafka volume ownership and IPv6 frontend health probing prevented reliable startup. | Nonroot Kafka image initializes its persistent directory; frontend probe explicitly uses loopback IPv4. All nine containers become healthy. |
| Low | Documentation drift: risk transaction timing, payment version default, recipientEmail, stale progress notes and diagram punctuation. | Matched docs to migrations/contracts; rendered both Mermaid diagrams in a browser and fixed sequence syntax; replaced progress notes with measured evidence. |
| Low | Dense source formatting and generated TypeScript metadata reduced repository readability. | Formatted Java/frontend/support scripts, retained feature boundaries and ignored tsbuildinfo. All verification gates still pass. |

Dependency references: [Zod advisory](https://github.com/advisories/GHSA-mrhx-c7xc-m3w5), [Tinypool advisory](https://github.com/advisories/GHSA-85c8-ppgw-ccpr). The minimatch override targets only TypeScript ESLint's parser dependency; revisit it when the upstream package adopts the patched major release. CI runs npm audit so future advisories are visible.

## Verification and limits

- Full Java reactor: 42 tests, zero failures/errors/skips; real Docker Postgres/Redis/Kafka integration coverage.
- Frontend: lint, 21 tests, production build; dependency audit zero findings.
- Live curl walkthrough: five gateway pings, registration/login/me, top-up, transfer/history/replay, changed-body422, outsider404, spoofed identity protection, 20 concurrent retries and notifications for both participants.
- Browser: login/register, funding, send/review/result, transaction timeline, notification read state, light/dark and 360 px mobile navigation. Screenshots use actual demo records.
- k6: 975 requests, 0% unexpected errors, payment p95 127.24 ms; post-load journal invariants all pass. See [measured results](../load-tests/RESULTS.md).
- Workflow YAML and Compose configuration validated locally. A hosted GitHub Actions run has not occurred because this checkout is not connected to a GitHub repository.

No blocking open questions remain. The single-broker local deployment has no availability guarantee. Long soak tests, automated browser regression in CI, process-kill chaos tests across multiple instances, and external security scanning are future extensions; the existing tests cover modeled timeout, rollback, cache-loss and duplicate-delivery failure paths. JWT refresh, real banking rails, FX, refunds, external email/SMS and RiskLens are deliberately outside V1. Demo test funds are explicitly opt-in. No claim of exactly-once Kafka transport is made.

## Interview explanation

Ordered locks prevent two transfers from each holding one wallet while waiting for the other: both must first acquire the same lower-ID wallet. The ledger invariant checks that credits and debits cancel per currency and that every cached balance equals its journal sum. Together with the concurrency tests, this checks conservation and overdraft protection under the tested workloads; conservation alone would not detect sending money to the wrong recipient, so the tests also verify account identity and transfer pairing.
