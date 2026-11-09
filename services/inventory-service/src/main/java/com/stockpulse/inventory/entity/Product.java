package com.stockpulse.inventory.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 80)
    private String sku;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Product() {
    }

    public Product(String sku, String name) {
        this.id = UUID.randomUUID();
        this.sku = sku;
        this.name = name;
        this.active = true;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }

    public void deactivate() { this.active = false; }
}
