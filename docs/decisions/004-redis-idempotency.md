# ADR 004: Redis idempotency with a durable safeguard

Status: Accepted (Phase 0).

## Context

Redis alone can lose a key after a crash and cause another debit.

## Decision

Redis caches original responses; PostgreSQL uniquely reserves user+operation+key and stores request hash/response for 24h.

## Alternatives and consequences

Extra database writes. Populate Redis only after the response transaction commits. If a process dies after a terminal payment commit but before saving its HTTP response, retry reconstructs the response from the immutable terminal payment. A CREATED payment remains in flight until reconciliation resolves it. Original 202 is replayed; clients GET current state. After expiry a key may start a new operation.

## Interview explanation

The key identifies an attempt, the body hash prevents changing that attempt, and durable uniqueness survives cache loss.
