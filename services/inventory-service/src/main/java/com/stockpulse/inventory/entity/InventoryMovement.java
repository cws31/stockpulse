package com.stockpulse.inventory.entity;

import com.stockpulse.inventory.domain.MovementType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_movements",
       indexes = @Index(name = "idx_movement_product_created", columnList = "product_id, created_at"))
public class InventoryMovement {

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "reservation_id")
    private UUID reservationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MovementType type;

    @Column(nullable = false)
    private long quantity;

    @Column(length = 250)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected InventoryMovement() {
    }

    public InventoryMovement(UUID productId, UUID reservationId, MovementType type,
                             long quantity, String reason) {
        this.id = UUID.randomUUID();
        this.productId = productId;
        this.reservationId = reservationId;
        this.type = type;
        this.quantity = quantity;
        this.reason = reason;
        this.createdAt = Instant.now();
    }
}
