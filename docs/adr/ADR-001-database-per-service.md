
# ADR-001: Database Per Service

Status: Accepted

## Context

StockPulse contains independently deployable microservices.
Shared database ownership would create tight coupling.

## Decision

Each service owns its database.

- Order Service: MySQL
- Payment Service: MySQL
- Inventory Service: PostgreSQL

Order and Payment use separate databases, users and permissions.

## Consequences

Positive:
- Independent schema evolution.
- Service-level data ownership.
- Reduced coupling.
- Independent migrations.

Trade-offs:
- No cross-service database joins.
- Eventual consistency across services.
- Additional infrastructure management.
