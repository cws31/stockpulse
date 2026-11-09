package com.stockpulse.inventory.entity;

import com.stockpulse.inventory.domain.ReservationStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservations",
       uniqueConstraints = @UniqueConstraint(name = "uk_reservation_idempotency_key",
                                             columnNames = "idempotency_key"),
       indexes = {
           @Index(name = "idx_reservation_expiry", columnList = "status, expires_at"),
           @Index(name = "idx_reservation_order", columnList = "order_id")
       })
public class Reservation {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, length = 100)
    private String orderId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private long quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "idempotency_key", nullable = false, length = 150)
    private String idempotencyKey;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Reservation() {
    }

    public Reservation(String orderId, UUID productId, long quantity,
                       String idempotencyKey, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
        this.idempotencyKey = idempotencyKey;
        this.status = ReservationStatus.ACTIVE;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() { return id; }
    public String getOrderId() { return orderId; }
    public UUID getProductId() { return productId; }
    public long getQuantity() { return quantity; }
    public ReservationStatus getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void confirm() {
        requireActive();
        status = ReservationStatus.CONFIRMED;
        updatedAt = Instant.now();
    }

    public void release() {
        requireActive();
        status = ReservationStatus.RELEASED;
        updatedAt = Instant.now();
    }

    public void expire() {
        requireActive();
        status = ReservationStatus.EXPIRED;
        updatedAt = Instant.now();
    }

    private void requireActive() {
        if (status != ReservationStatus.ACTIVE) {
            throw new IllegalStateException("Reservation is not active");
        }
    }
}
