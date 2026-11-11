# ADR 005: Transactional outbox

Status: Accepted (Phase 0).

## Context

A direct Kafka send and database commit cannot be made atomic by wishful ordering.

## Decision

Commit terminal payment state, transition and outbox event together; publish with acknowledgment and deduplicate notification effects.

## Alternatives and consequences

Event delivery is delayed and can duplicate. Need publisher retry, backlog metrics, and receipt retention.

## Interview explanation

The database stores both what happened and our promise to tell other services. Retrying that promise is safe because recipients recognize the event ID.
