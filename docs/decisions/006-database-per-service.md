# ADR 006: Database per service

Status: Accepted (Phase 0).

## Context

Shared-table access couples schema changes and bypasses ownership/security.

## Decision

Separate databases/roles for stateful services; synchronous REST and asynchronous Kafka are the only cross-service interfaces.

## Alternatives and consequences

No cross-database FKs/transactions. Provisioning and payments need explicit recovery.

## Interview explanation

Ledger alone owns money. Other services ask it to act, rather than secretly changing its tables.
