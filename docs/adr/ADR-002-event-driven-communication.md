
# ADR-002: Event-Driven Communication

Status: Accepted

## Context

Order processing involves multiple independent services.
Synchronous chains create runtime coupling and failure propagation.

## Decision

Use:
- REST for synchronous client-facing operations.
- Apache Kafka for asynchronous business events.
- Transactional Outbox for reliable event publishing.
- Idempotent consumers for duplicate delivery handling.

## Consequences

Positive:
- Loose coupling.
- Independent service recovery.
- Asynchronous processing.
- Better failure isolation.

Trade-offs:
- Eventual consistency.
- More complex debugging.
- Event versioning requirements.
- Duplicate event handling is mandatory.
