package com.stockpulse.inventory.repository;

import com.stockpulse.inventory.entity.Inventory;
import com.stockpulse.inventory.entity.Product;
import com.stockpulse.inventory.entity.Reservation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class InventoryPersistenceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void registerDatabaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private ProductRepository productRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void flywaySchemaPersistsProductAndInventory() {
        Product product = productRepository.saveAndFlush(new Product("PERSIST-1", "Persisted product"));
        inventoryRepository.saveAndFlush(new Inventory(product.getId(), 14));

        Inventory stored = inventoryRepository.findByProductId(product.getId()).orElseThrow();
        assertAll(
                () -> assertEquals(product.getId(), stored.getProductId()),
                () -> assertEquals(14, stored.getTotalStock()),
                () -> assertEquals(14, stored.getAvailableStock()),
                () -> assertEquals(0, stored.getReservedStock()));
    }

    @Test
    void inventoryRepositoryCanAcquirePessimisticWriteLock() {
        Product product = productRepository.saveAndFlush(new Product("LOCK-1", "Lock test"));
        inventoryRepository.saveAndFlush(new Inventory(product.getId(), 5));

        Inventory locked = inventoryRepository.findByProductIdForUpdate(product.getId()).orElseThrow();
        assertEquals(5, locked.getAvailableStock());
    }

    @Test
    void databaseRejectsInventoryBalanceThatBreaksInvariant() {
        Product product = productRepository.saveAndFlush(new Product("CHECK-1", "Constraint test"));
        UUID inventoryId = UUID.randomUUID();

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO inventory (id, product_id, total_stock, available_stock, reserved_stock, version, updated_at)
                VALUES (?, ?, 10, 4, 4, 0, ?)
                """, inventoryId, product.getId(), Timestamp.from(Instant.now())));
    }

    @Test
    void databaseEnforcesUniqueReservationIdempotencyKey() {
        Product product = productRepository.saveAndFlush(new Product("IDEMPOTENCY-DB-1", "Idempotency test"));
        String key = "DB-IDEMPOTENCY-" + UUID.randomUUID();
        reservationRepository.saveAndFlush(new Reservation("ORDER-DB-1", product.getId(), 1,
                key, Instant.now().plusSeconds(300)));

        assertThrows(DataIntegrityViolationException.class, () -> reservationRepository.saveAndFlush(
                new Reservation("ORDER-DB-2", product.getId(), 1, key, Instant.now().plusSeconds(300))));
    }

    @Test
    void databaseEnforcesUniqueProductSku() {
        productRepository.saveAndFlush(new Product("SKU-UNIQUE-DB", "First product"));
        assertThrows(DataIntegrityViolationException.class, () -> productRepository.saveAndFlush(
                new Product("SKU-UNIQUE-DB", "Duplicate product")));
    }
}
