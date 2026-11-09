package com.stockpulse.inventory.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory",
       uniqueConstraints = @UniqueConstraint(name = "uk_inventory_product", columnNames = "product_id"))
public class Inventory {

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "total_stock", nullable = false)
    private long totalStock;

    @Column(name = "available_stock", nullable = false)
    private long availableStock;

    @Column(name = "reserved_stock", nullable = false)
    private long reservedStock;

    @Version
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Inventory() {
    }

    public Inventory(UUID productId, long initialStock) {
        if (productId == null) {
            throw new IllegalArgumentException("Product ID is required");
        }
        if (initialStock < 0) {
            throw new IllegalArgumentException("Initial stock cannot be negative");
        }
        this.id = UUID.randomUUID();
        this.productId = productId;
        this.totalStock = initialStock;
        this.availableStock = initialStock;
        this.reservedStock = 0;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getProductId() { return productId; }
    public long getTotalStock() { return totalStock; }
    public long getAvailableStock() { return availableStock; }
    public long getReservedStock() { return reservedStock; }
    public long getVersion() { return version; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void reserve(long quantity) {
        if (quantity <= 0 || availableStock < quantity) {
            throw new IllegalArgumentException("Insufficient available stock");
        }
        availableStock -= quantity;
        reservedStock += quantity;
        updatedAt = Instant.now();
    }

    public void confirm(long quantity) {
        if (quantity <= 0 || reservedStock < quantity || totalStock < quantity) {
            throw new IllegalStateException("Inventory reservation invariant violated");
        }
        reservedStock -= quantity;
        totalStock -= quantity;
        updatedAt = Instant.now();
    }

    public void release(long quantity) {
        if (quantity <= 0 || reservedStock < quantity) {
            throw new IllegalStateException("Inventory reservation invariant violated");
        }
        reservedStock -= quantity;
        availableStock += quantity;
        updatedAt = Instant.now();
    }

    public void adjust(long delta) {
        if (delta == 0) {
            throw new IllegalArgumentException("Adjustment quantity cannot be zero");
        }
        if (delta == Long.MIN_VALUE) {
            throw new IllegalArgumentException("Adjustment quantity is outside the supported range");
        }
        if (delta < 0 && availableStock < -delta) {
            throw new IllegalArgumentException("Adjustment would reduce available stock below zero");
        }
        try {
            totalStock = Math.addExact(totalStock, delta);
            availableStock = Math.addExact(availableStock, delta);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Stock quantity exceeds the supported range", exception);
        }
        updatedAt = Instant.now();
        if (totalStock < 0 || availableStock < 0 || reservedStock < 0) {
            throw new IllegalStateException("Inventory invariant violated");
        }
    }
}
