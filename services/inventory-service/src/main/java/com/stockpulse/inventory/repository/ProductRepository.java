package com.stockpulse.inventory.repository;

import com.stockpulse.inventory.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    boolean existsBySku(String sku);
    Optional<Product> findByIdAndActiveTrue(UUID id);
}
