# ADR 001: Minor-unit money

Status: Accepted (Phase 0).

## Context

Floating-point arithmetic introduces rounding; JavaScript numbers cannot represent every long.

## Decision

Store Java long/Postgres BIGINT, currency INR, exact checked arithmetic; decimal strings at JSON boundaries.

## Alternatives and consequences

BigInt/string parsing in the UI costs some code. Reject overflow rather than silently wrap.

## Interview explanation

Money is an integer count of paise. We never let binary floating-point decide how much someone owns.
