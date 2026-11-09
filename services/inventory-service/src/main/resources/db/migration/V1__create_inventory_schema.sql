CREATE TABLE products (
    id UUID PRIMARY KEY,
    sku VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE inventory (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL UNIQUE REFERENCES products(id),
    total_stock BIGINT NOT NULL CHECK (total_stock >= 0),
    available_stock BIGINT NOT NULL CHECK (available_stock >= 0),
    reserved_stock BIGINT NOT NULL CHECK (reserved_stock >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_inventory_balance
        CHECK (total_stock = available_stock + reserved_stock)
);

CREATE TABLE reservations (
    id UUID PRIMARY KEY,
    order_id VARCHAR(100) NOT NULL,
    product_id UUID NOT NULL REFERENCES products(id),
    quantity BIGINT NOT NULL CHECK (quantity > 0),
    status VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(150) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_reservation_expiry ON reservations(status, expires_at);
CREATE INDEX idx_reservation_order ON reservations(order_id);

CREATE TABLE inventory_movements (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(id),
    reservation_id UUID,
    type VARCHAR(30) NOT NULL,
    quantity BIGINT NOT NULL,
    reason VARCHAR(250),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_movement_product_created
    ON inventory_movements(product_id, created_at);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_version INTEGER NOT NULL DEFAULT 1,
    correlation_id VARCHAR(100),
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_status_created
    ON outbox_events(status, created_at);
