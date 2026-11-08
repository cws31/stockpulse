
# StockPulse Development Guidelines

## 1. Engineering Principles

- Readability over cleverness.
- Proper domain and entity modeling.
- Modularity and extensibility.
- Separation of concerns.
- Explicit error handling.
- Corner-case handling.
- Correctness before optimization.

## 2. Service Independence

Every service must have:

- Independent Maven build.
- Independent Dockerfile.
- Independent configuration.
- Independent database ownership.
- Independent migrations.
- Independent tests.
- Independent deployment lifecycle.

No service may access another service's database.

## 3. Technology Standards

- Java 21
- Spring Boot 3.x (exact version pinned per service)
- Maven
- REST for synchronous communication
- Kafka for asynchronous communication
- MySQL for Order and Payment
- PostgreSQL for Inventory
- Redis for caching and rate limiting
- Flyway for database migrations
- JUnit 5 and Testcontainers

## 4. Coding Standards

- Use constructor injection.
- Keep controllers thin.
- Keep business logic in application/domain layers.
- Use DTOs for external communication.
- Avoid exposing persistence entities through APIs.
- Use meaningful names.
- Avoid unnecessary abstractions.
- Centralize exception handling.
- Externalize configuration.
- Never commit secrets.

## 5. Database Standards

- Each service owns its schema.
- Use Flyway for schema changes.
- Use transactions for local business operations.
- Define appropriate indexes and constraints.
- Never perform cross-service SQL joins.
- Never modify another service's database directly.

## 6. Event Standards

Every business event must contain:

- eventId
- eventType
- eventVersion
- aggregateId
- occurredAt
- correlationId
- payload

Consumers must handle duplicate deliveries safely.

Event contracts must remain backward compatible.

## 7. Testing Standards

Mandatory:

- Unit tests
- Repository integration tests
- API integration tests
- Event consumer tests
- Idempotency tests
- Failure scenario tests

Critical business invariants must have automated tests.

## 8. Development Checkpoint Rules

For every service:

1. Requirements
2. Design approval
3. Implementation
4. Unit testing
5. Integration testing
6. Failure testing
7. Verification
8. Documentation
9. Approval to proceed

Never start the next service before the current checkpoint passes.

## 9. Definition of Done

A feature is complete only when:

- Code compiles.
- Tests pass.
- Edge cases are covered.
- Database behavior is verified.
- API/event contracts are documented.
- No unresolved critical defects remain.
