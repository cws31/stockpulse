# StockPulse Inventory Service

Independent Spring Boot service responsible for product inventory, stock adjustments,
reservations, reservation expiry, and inventory event publication.

## Technology

- Java 21
- Spring Boot 3.4.5
- Maven
- PostgreSQL 17
- Flyway
- Kafka
- JUnit 5 / Testcontainers

## Run against the StockPulse foundation

The existing Docker Compose infrastructure should be running.

From this directory:

```bash
mvn clean verify
mvn spring-boot:run
```

Default connections:

- PostgreSQL: `localhost:5432`
- Database: `stockpulse_inventory`
- User: `inventory_service`
- Kafka: `localhost:9092`
- HTTP: `localhost:8082`

Override credentials through environment variables:

- `INVENTORY_DB_URL`
- `INVENTORY_DB_USERNAME`
- `INVENTORY_DB_PASSWORD`
- `KAFKA_BOOTSTRAP_SERVERS`
- `RESERVATION_TTL_SECONDS` (default: 300)

## API

### Create product and initial stock

`POST /api/inventory/products`

```json
{
  "sku": "LAPTOP-001",
  "name": "Laptop",
  "initialStock": 10
}
```

### Read stock

`GET /api/inventory/products/{productId}/stock`

### Adjust stock

`POST /api/inventory/stock/adjustments`

```json
{
  "productId": "PRODUCT_UUID",
  "quantity": 5,
  "reason": "New stock received"
}
```

A negative adjustment removes available stock and is rejected if it would make available stock negative.

### Reserve stock

`POST /api/inventory/reservations`

```json
{
  "orderId": "ORDER-1001",
  "productId": "PRODUCT_UUID",
  "quantity": 2,
  "idempotencyKey": "ORDER-1001-PRODUCT-001"
}
```

Optional header: `X-Correlation-Id`.

### Confirm / release

- `POST /api/inventory/reservations/{reservationId}/confirm`
- `POST /api/inventory/reservations/{reservationId}/release`
- `GET /api/inventory/reservations/{reservationId}`

## Business rules

- `totalStock = availableStock + reservedStock`
- Stock quantities cannot be negative.
- Reservation requests use a configurable TTL (default five minutes).
- Inventory rows are locked during stock-changing operations.
- Reservation idempotency keys are unique.
- Confirmed reservations consume total and reserved stock.
- Released and expired reservations return stock to available stock.
- Inventory changes and outbox events are committed in one PostgreSQL transaction.

## Events

Events are published to Kafka topic `inventory-events` through the transactional outbox.

Current event types include:
- `StockInitialized`
- `StockAdjusted`
- `InventoryReserved`
- `InventoryRejected`
- `ReservationConfirmed`
- `ReservationReleased`
- `ReservationExpired`

Delivery is at-least-once. Consumers must deduplicate by event ID when the event envelope is added to the shared contract.

## Notes

This is the first independently deployable Inventory Service implementation.
Authentication is expected to be enforced at the API Gateway in the initial local-development phase.
