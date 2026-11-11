# ADR 003: Ordered pessimistic locks

Status: Accepted (Phase 0).

## Context

Checking first without locks lets two requests spend the same balance.

## Decision

Serialize transfer identity, then lock account rows in ascending PostgreSQL UUID order before checking funds.

## Alternatives and consequences

Hot wallets serialize. All paths must preserve lock order; retries reuse transfer IDs.

## Interview explanation

Two payments cannot both spend the same funds because one waits. One global lock order prevents A-waits-for-B/B-waits-for-A cycles.
