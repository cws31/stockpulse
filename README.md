
# StockPulse

A distributed inventory reservation and order processing system built
to demonstrate real-world distributed systems engineering.

## Architecture

Independent services:

- API Gateway
- Inventory Service
- Order Service
- Payment Service
- Notification Service

## Technology Stack

- Java 21
- Spring Boot
- Maven
- MySQL 8.4
- PostgreSQL 17
- Apache Kafka 4.1
- Redis 7
- Docker Compose
- Flyway
- JUnit 5
- Testcontainers
- Prometheus
- Grafana
- OpenTelemetry
- Jaeger

## Infrastructure

| Component | Host | Port |
|---|---|---|
| MySQL | localhost | 3307 |
| PostgreSQL | localhost | 5432 |
| Kafka | localhost | 9092 |
| Redis | localhost | 6379 |

Database ownership:

- Order Service: stockpulse_order
- Payment Service: stockpulse_payment
- Inventory Service: stockpulse_inventory

## Start Infrastructure

```bash
docker compose --env-file .env \
-f infrastructure/docker/docker-compose.yml up -d
```

## Stop Infrastructure

```bash
docker compose --env-file .env \
-f infrastructure/docker/docker-compose.yml down
```

Persistent volumes are retained when stopping.

## Project Principles

1. Independent service ownership.
2. Database per service.
3. No cross-service database access.
4. Event-driven asynchronous communication.
5. Transactional consistency within each service.
6. Idempotent event processing.
7. Failure recovery and observability.
8. Test before moving to the next development checkpoint.

## Development Workflow

WRITE -> TEST -> VERIFY -> MOVE

See DEVELOPMENT.md for engineering standards.
