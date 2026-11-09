package com.stockpulse.inventory.repository;

import com.stockpulse.inventory.entity.InventoryMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, UUID> {
}
