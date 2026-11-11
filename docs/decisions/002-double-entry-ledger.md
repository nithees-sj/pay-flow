# ADR 002: Double-entry ledger

Status: Accepted (Phase 0).

## Context

A mutable balance without a journal loses the audit trail.

## Decision

Every POSTED transfer creates one debit and one credit in one transaction, with deferred integrity checks and transactional cached balances.

## Alternatives and consequences

More writes and invariant tests; system funding is the only negative signed account.

## Interview explanation

Every rupee has a source and destination. We can reconstruct every balance and prove that transfers do not create money.
