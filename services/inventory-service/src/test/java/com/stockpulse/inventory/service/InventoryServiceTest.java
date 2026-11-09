package com.stockpulse.inventory.service;

import com.stockpulse.inventory.domain.ReservationStatus;
import com.stockpulse.inventory.dto.*;
import com.stockpulse.inventory.entity.*;
import com.stockpulse.inventory.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private InventoryMovementRepository movementRepository;
    @Mock private OutboxService outboxService;

    private InventoryService service;

    @BeforeEach
    void setUp() {
        service = new InventoryService(productRepository, inventoryRepository,
                reservationRepository, movementRepository, outboxService, 300);
    }

    @Test
    void createProductPersistsProductInventoryMovementAndEvent() {
        when(productRepository.existsBySku("SKU-1")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = service.createProduct(new CreateProductRequest("SKU-1", "Keyboard", 5));

        assertAll(
                () -> assertEquals("SKU-1", response.sku()),
                () -> assertEquals(5, response.totalStock()),
                () -> assertEquals(5, response.availableStock()),
                () -> verify(movementRepository).save(any(InventoryMovement.class)),
                () -> verify(outboxService).add(eq(response.productId().toString()), eq("StockInitialized"), isNull(), any()));
    }

    @Test
    void createProductAllowsZeroInitialStockWithoutMovement() {
        when(productRepository.existsBySku("SKU-ZERO")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = service.createProduct(new CreateProductRequest("SKU-ZERO", "New item", 0));

        assertEquals(0, response.totalStock());
        verify(movementRepository, never()).save(any());
    }

    @Test
    void createProductRejectsDuplicateSku() {
        when(productRepository.existsBySku("SKU-1")).thenReturn(true);
        assertThrows(ConflictException.class,
                () -> service.createProduct(new CreateProductRequest("SKU-1", "Keyboard", 5)));
        verify(productRepository, never()).save(any());
    }

    @Test
    void getProductReturnsNotFoundWhenProductDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(productRepository.findById(id)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.getProduct(id));
    }

    @Test
    void getStockReturnsNotFoundWhenInventoryDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(inventoryRepository.findByProductId(id)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.getStock(id));
    }

    @Test
    void positiveStockAdjustmentUpdatesInventoryAndWritesAuditEvent() {
        UUID productId = UUID.randomUUID();
        Inventory inventory = new Inventory(productId, 5);
        when(inventoryRepository.findByProductIdForUpdate(productId)).thenReturn(Optional.of(inventory));

        StockResponse response = service.adjustStock(new StockAdjustmentRequest(productId, 4, "Goods received"));

        assertAll(
                () -> assertEquals(9, response.totalStock()),
                () -> assertEquals(9, response.availableStock()),
                () -> verify(movementRepository).save(any(InventoryMovement.class)),
                () -> verify(outboxService).add(eq(productId.toString()), eq("StockAdjusted"), isNull(), any()));
    }

    @Test
    void stockAdjustmentThatConsumesReservedUnitsReturnsConflict() {
        UUID productId = UUID.randomUUID();
        Inventory inventory = new Inventory(productId, 5);
        inventory.reserve(3);
        when(inventoryRepository.findByProductIdForUpdate(productId)).thenReturn(Optional.of(inventory));

        assertThrows(ConflictException.class,
                () -> service.adjustStock(new StockAdjustmentRequest(productId, -3, "Bad correction")));
        assertEquals(5, inventory.getTotalStock());
        verify(movementRepository, never()).save(any());
    }

    @Test
    void reserveCreatesReservationAndMovesStock() {
        UUID productId = UUID.randomUUID();
        Inventory inventory = new Inventory(productId, 10);
        Product product = new Product("SKU-1", "Keyboard");
        when(reservationRepository.findByIdempotencyKey("KEY-1"))
                .thenReturn(Optional.empty(), Optional.empty());
        when(inventoryRepository.findByProductIdForUpdate(productId)).thenReturn(Optional.of(inventory));
        when(productRepository.findByIdAndActiveTrue(productId)).thenReturn(Optional.of(product));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponse response = service.reserve(request(productId, 3, "KEY-1", "ORDER-1"), "trace-1");

        assertAll(
                () -> assertNotNull(response.reservationId()),
                () -> assertEquals(ReservationStatus.ACTIVE, response.status()),
                () -> assertEquals(7, inventory.getAvailableStock()),
                () -> assertEquals(3, inventory.getReservedStock()),
                () -> verify(outboxService).add(eq("ORDER-1"), eq("InventoryReserved"), eq("trace-1"), any()));
    }

    @Test
    void insufficientStockDoesNotCreateReservationOrChangeBalances() {
        UUID productId = UUID.randomUUID();
        Inventory inventory = new Inventory(productId, 2);
        when(reservationRepository.findByIdempotencyKey("KEY-2"))
                .thenReturn(Optional.empty(), Optional.empty());
        when(inventoryRepository.findByProductIdForUpdate(productId)).thenReturn(Optional.of(inventory));
        when(productRepository.findByIdAndActiveTrue(productId)).thenReturn(Optional.of(new Product("SKU-2", "Mouse")));

        ReservationResponse response = service.reserve(request(productId, 3, "KEY-2", "ORDER-2"), null);

        assertAll(
                () -> assertNull(response.reservationId()),
                () -> assertNull(response.status()),
                () -> assertEquals(2, inventory.getAvailableStock()),
                () -> assertEquals(0, inventory.getReservedStock()),
                () -> verify(reservationRepository, never()).save(any()),
                () -> verify(outboxService).add(eq("ORDER-2"), eq("InventoryRejected"), isNull(), any()));
    }

    @Test
    void reserveReturnsExistingReservationForSameIdempotencyRequest() {
        UUID productId = UUID.randomUUID();
        Reservation existing = reservation(productId, "ORDER-3", 2, "KEY-3", Instant.now().plusSeconds(300));
        when(reservationRepository.findByIdempotencyKey("KEY-3")).thenReturn(Optional.of(existing));

        ReservationResponse response = service.reserve(request(productId, 2, "KEY-3", "ORDER-3"), null);

        assertEquals(existing.getId(), response.reservationId());
        assertEquals("Existing reservation returned", response.message());
        verify(inventoryRepository, never()).findByProductIdForUpdate(any());
    }

    @Test
    void reserveRejectsReusingIdempotencyKeyForDifferentPayload() {
        UUID productId = UUID.randomUUID();
        Reservation existing = reservation(productId, "ORDER-4", 2, "KEY-4", Instant.now().plusSeconds(300));
        when(reservationRepository.findByIdempotencyKey("KEY-4")).thenReturn(Optional.of(existing));

        assertThrows(ConflictException.class,
                () -> service.reserve(request(productId, 3, "KEY-4", "ORDER-4"), null));
        verify(inventoryRepository, never()).findByProductIdForUpdate(any());
    }

    @Test
    void reserveRechecksIdempotencyAfterWaitingForInventoryLock() {
        UUID productId = UUID.randomUUID();
        Inventory inventory = new Inventory(productId, 10);
        Reservation existing = reservation(productId, "ORDER-5", 2, "KEY-5", Instant.now().plusSeconds(300));
        when(reservationRepository.findByIdempotencyKey("KEY-5"))
                .thenReturn(Optional.empty(), Optional.of(existing));
        when(inventoryRepository.findByProductIdForUpdate(productId)).thenReturn(Optional.of(inventory));

        ReservationResponse response = service.reserve(request(productId, 2, "KEY-5", "ORDER-5"), null);

        assertEquals(existing.getId(), response.reservationId());
        assertEquals(10, inventory.getAvailableStock());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void reserveRejectsInactiveProduct() {
        UUID productId = UUID.randomUUID();
        when(reservationRepository.findByIdempotencyKey("KEY-6"))
                .thenReturn(Optional.empty(), Optional.empty());
        when(inventoryRepository.findByProductIdForUpdate(productId))
                .thenReturn(Optional.of(new Inventory(productId, 5)));
        when(productRepository.findByIdAndActiveTrue(productId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> service.reserve(request(productId, 1, "KEY-6", "ORDER-6"), null));
    }

    @Test
    void confirmConsumesReservedStockAndChangesReservationState() {
        UUID productId = UUID.randomUUID();
        Inventory inventory = new Inventory(productId, 10);
        inventory.reserve(2);
        Reservation reservation = reservation(productId, "ORDER-7", 2, "KEY-7", Instant.now().plusSeconds(300));
        when(reservationRepository.findByIdForUpdate(reservation.getId())).thenReturn(Optional.of(reservation));
        when(inventoryRepository.findByProductIdForUpdate(productId)).thenReturn(Optional.of(inventory));

        ReservationResponse response = service.confirm(reservation.getId(), "trace-7");

        assertAll(
                () -> assertEquals(ReservationStatus.CONFIRMED, response.status()),
                () -> assertEquals(8, inventory.getTotalStock()),
                () -> assertEquals(8, inventory.getAvailableStock()),
                () -> assertEquals(0, inventory.getReservedStock()));
    }

    @Test
    void confirmingAlreadyConfirmedReservationIsIdempotent() {
        UUID productId = UUID.randomUUID();
        Reservation reservation = reservation(productId, "ORDER-8", 2, "KEY-8", Instant.now().plusSeconds(300));
        reservation.confirm();
        when(reservationRepository.findByIdForUpdate(reservation.getId())).thenReturn(Optional.of(reservation));

        ReservationResponse response = service.confirm(reservation.getId(), null);

        assertEquals("Reservation already confirmed", response.message());
        verify(inventoryRepository, never()).findByProductIdForUpdate(any());
    }

    @Test
    void confirmingExpiredReservationExpiresItAndReturnsStock() {
        UUID productId = UUID.randomUUID();
        Inventory inventory = new Inventory(productId, 10);
        inventory.reserve(2);
        Reservation reservation = reservation(productId, "ORDER-9", 2, "KEY-9", Instant.now().minusSeconds(1));
        when(reservationRepository.findByIdForUpdate(reservation.getId())).thenReturn(Optional.of(reservation));
        when(inventoryRepository.findByProductIdForUpdate(productId)).thenReturn(Optional.of(inventory));

        ReservationResponse response = service.confirm(reservation.getId(), null);

        assertAll(
                () -> assertEquals(ReservationStatus.EXPIRED, response.status()),
                () -> assertEquals(10, inventory.getAvailableStock()),
                () -> assertEquals(0, inventory.getReservedStock()));
    }

    @Test
    void confirmRejectsReleasedReservation() {
        Reservation reservation = reservation(UUID.randomUUID(), "ORDER-10", 1, "KEY-10", Instant.now().plusSeconds(300));
        reservation.release();
        when(reservationRepository.findByIdForUpdate(reservation.getId())).thenReturn(Optional.of(reservation));
        assertThrows(ConflictException.class, () -> service.confirm(reservation.getId(), null));
    }

    @Test
    void releaseReturnsStockAndChangesReservationState() {
        UUID productId = UUID.randomUUID();
        Inventory inventory = new Inventory(productId, 10);
        inventory.reserve(2);
        Reservation reservation = reservation(productId, "ORDER-11", 2, "KEY-11", Instant.now().plusSeconds(300));
        when(reservationRepository.findByIdForUpdate(reservation.getId())).thenReturn(Optional.of(reservation));
        when(inventoryRepository.findByProductIdForUpdate(productId)).thenReturn(Optional.of(inventory));

        ReservationResponse response = service.release(reservation.getId(), null);

        assertAll(
                () -> assertEquals(ReservationStatus.RELEASED, response.status()),
                () -> assertEquals(10, inventory.getAvailableStock()),
                () -> assertEquals(0, inventory.getReservedStock()));
    }

    @Test
    void releasingAlreadyReleasedReservationDoesNotChangeStockAgain() {
        Reservation reservation = reservation(UUID.randomUUID(), "ORDER-12", 2, "KEY-12", Instant.now().plusSeconds(300));
        reservation.release();
        when(reservationRepository.findByIdForUpdate(reservation.getId())).thenReturn(Optional.of(reservation));

        ReservationResponse response = service.release(reservation.getId(), null);

        assertEquals("Reservation already released", response.message());
        verify(inventoryRepository, never()).findByProductIdForUpdate(any());
    }

    @Test
    void confirmedReservationCannotBeReleased() {
        Reservation reservation = reservation(UUID.randomUUID(), "ORDER-13", 2, "KEY-13", Instant.now().plusSeconds(300));
        reservation.confirm();
        when(reservationRepository.findByIdForUpdate(reservation.getId())).thenReturn(Optional.of(reservation));
        assertThrows(ConflictException.class, () -> service.release(reservation.getId(), null));
    }

    @Test
    void expireReservationsReturnsExpiredQuantitiesToAvailableStock() {
        UUID productId = UUID.randomUUID();
        Inventory inventory = new Inventory(productId, 10);
        inventory.reserve(2);
        Reservation reservation = reservation(productId, "ORDER-14", 2, "KEY-14", Instant.now().minusSeconds(1));
        when(reservationRepository.findExpiredIds(any(), any(), any())).thenReturn(java.util.List.of(reservation.getId()));
        when(reservationRepository.findByIdForUpdate(reservation.getId())).thenReturn(Optional.of(reservation));
        when(inventoryRepository.findByProductIdForUpdate(productId)).thenReturn(Optional.of(inventory));

        service.expireReservations();

        assertAll(
                () -> assertEquals(ReservationStatus.EXPIRED, reservation.getStatus()),
                () -> assertEquals(10, inventory.getAvailableStock()),
                () -> assertEquals(0, inventory.getReservedStock()));
    }

    private ReservationRequest request(UUID productId, long quantity, String key, String orderId) {
        return new ReservationRequest(orderId, productId, quantity, key);
    }

    private Reservation reservation(UUID productId, String orderId, long quantity,
                                    String key, Instant expiresAt) {
        return new Reservation(orderId, productId, quantity, key, expiresAt);
    }
}
