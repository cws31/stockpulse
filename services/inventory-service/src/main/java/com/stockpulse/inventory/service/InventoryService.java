package com.stockpulse.inventory.service;

import com.stockpulse.inventory.domain.MovementType;
import com.stockpulse.inventory.domain.ReservationStatus;
import com.stockpulse.inventory.dto.*;
import com.stockpulse.inventory.entity.*;
import com.stockpulse.inventory.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class InventoryService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final ReservationRepository reservationRepository;
    private final InventoryMovementRepository movementRepository;
    private final OutboxService outboxService;
    private final long reservationTtlSeconds;

    public InventoryService(ProductRepository productRepository,
                            InventoryRepository inventoryRepository,
                            ReservationRepository reservationRepository,
                            InventoryMovementRepository movementRepository,
                            OutboxService outboxService,
                            @Value("${stockpulse.reservation.ttl-seconds:300}") long reservationTtlSeconds) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.reservationRepository = reservationRepository;
        this.movementRepository = movementRepository;
        this.outboxService = outboxService;
        this.reservationTtlSeconds = reservationTtlSeconds;
    }

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new ConflictException("A product with this SKU already exists");
        }

        Product product = productRepository.save(new Product(request.sku(), request.name()));
        Inventory inventory = inventoryRepository.save(
                new Inventory(product.getId(), request.initialStock()));

        if (request.initialStock() > 0) {
            movementRepository.save(new InventoryMovement(product.getId(), null,
                    MovementType.INITIAL_STOCK, request.initialStock(), "Initial stock"));
        }

        outboxService.add(product.getId().toString(), "StockInitialized", null,
                Map.of("productId", product.getId(), "initialStock", request.initialStock()));

        return toProductResponse(product, inventory);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found"));
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new NotFoundException("Inventory not found"));
        return toProductResponse(product, inventory);
    }

    @Transactional(readOnly = true)
    public StockResponse getStock(UUID productId) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new NotFoundException("Inventory not found"));
        return toStockResponse(inventory);
    }

    @Transactional
    public StockResponse adjustStock(StockAdjustmentRequest request) {
        Inventory inventory = inventoryRepository.findByProductIdForUpdate(request.productId())
                .orElseThrow(() -> new NotFoundException("Inventory not found"));

        try {
            inventory.adjust(request.quantity());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new ConflictException(exception.getMessage());
        }

        movementRepository.save(new InventoryMovement(request.productId(), null,
                MovementType.STOCK_ADJUSTMENT, request.quantity(), request.reason()));

        outboxService.add(request.productId().toString(), "StockAdjusted", null,
                Map.of("productId", request.productId(), "quantityDelta", request.quantity(),
                        "reason", request.reason()));

        return toStockResponse(inventory);
    }

    @Transactional
    public ReservationResponse reserve(ReservationRequest request, String correlationId) {
        var existing = reservationRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            Reservation reservation = existing.get();
            if (!reservation.getOrderId().equals(request.orderId())
                    || !reservation.getProductId().equals(request.productId())
                    || reservation.getQuantity() != request.quantity()) {
                throw new ConflictException("Idempotency key was already used for a different request");
            }
            return toReservationResponse(reservation, "Existing reservation returned");
        }

        Inventory inventory = inventoryRepository.findByProductIdForUpdate(request.productId())
                .orElseThrow(() -> new NotFoundException("Inventory not found"));

        // A concurrent request with the same key may have committed while this request
        // was waiting for the inventory row lock. Re-check before changing stock.
        existing = reservationRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            Reservation reservation = existing.get();
            if (!reservation.getOrderId().equals(request.orderId())
                    || !reservation.getProductId().equals(request.productId())
                    || reservation.getQuantity() != request.quantity()) {
                throw new ConflictException("Idempotency key was already used for a different request");
            }
            return toReservationResponse(reservation, "Existing reservation returned");
        }

        if (!productRepository.findByIdAndActiveTrue(request.productId()).isPresent()) {
            throw new NotFoundException("Active product not found");
        }

        if (inventory.getAvailableStock() < request.quantity()) {
            outboxService.add(request.orderId(), "InventoryRejected", correlationId,
                    Map.of("orderId", request.orderId(), "productId", request.productId(),
                            "requestedQuantity", request.quantity(), "reason", "INSUFFICIENT_STOCK"));
            return new ReservationResponse(null, request.orderId(), request.productId(),
                    request.quantity(), null, null, "Insufficient available stock");
        }

        inventory.reserve(request.quantity());
        Reservation reservation = new Reservation(request.orderId(), request.productId(),
                request.quantity(), request.idempotencyKey(),
                Instant.now().plusSeconds(reservationTtlSeconds));
        reservationRepository.save(reservation);

        movementRepository.save(new InventoryMovement(request.productId(), reservation.getId(),
                MovementType.RESERVED, request.quantity(), "Order reservation"));

        outboxService.add(request.orderId(), "InventoryReserved", correlationId,
                Map.of("reservationId", reservation.getId(), "orderId", request.orderId(),
                        "productId", request.productId(), "quantity", request.quantity(),
                        "expiresAt", reservation.getExpiresAt()));

        return toReservationResponse(reservation, "Inventory reserved");
    }

    @Transactional
    public ReservationResponse confirm(UUID reservationId, String correlationId) {
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation not found"));

        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            return toReservationResponse(reservation, "Reservation already confirmed");
        }
        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new ConflictException("Only active reservations can be confirmed");
        }

        Inventory inventory = inventoryRepository.findByProductIdForUpdate(reservation.getProductId())
                .orElseThrow(() -> new NotFoundException("Inventory not found"));

        if (!reservation.getExpiresAt().isAfter(Instant.now())) {
            inventory.release(reservation.getQuantity());
            reservation.expire();
            movementRepository.save(new InventoryMovement(reservation.getProductId(),
                    reservation.getId(), MovementType.EXPIRED, reservation.getQuantity(),
                    "Reservation expired before confirmation"));
            outboxService.add(reservation.getOrderId(), "ReservationExpired", correlationId,
                    Map.of("reservationId", reservation.getId(), "orderId", reservation.getOrderId(),
                            "productId", reservation.getProductId(), "quantity", reservation.getQuantity()));
            return toReservationResponse(reservation, "Reservation expired; stock released");
        }

        inventory.confirm(reservation.getQuantity());
        reservation.confirm();
        movementRepository.save(new InventoryMovement(reservation.getProductId(),
                reservation.getId(), MovementType.CONFIRMED, reservation.getQuantity(),
                "Reservation confirmed"));
        outboxService.add(reservation.getOrderId(), "ReservationConfirmed", correlationId,
                Map.of("reservationId", reservation.getId(), "orderId", reservation.getOrderId(),
                        "productId", reservation.getProductId(), "quantity", reservation.getQuantity()));

        return toReservationResponse(reservation, "Reservation confirmed");
    }

    @Transactional
    public ReservationResponse release(UUID reservationId, String correlationId) {
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation not found"));

        if (reservation.getStatus() == ReservationStatus.RELEASED
                || reservation.getStatus() == ReservationStatus.EXPIRED) {
            return toReservationResponse(reservation, "Reservation already released");
        }
        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            throw new ConflictException("Confirmed reservation cannot be released");
        }

        Inventory inventory = inventoryRepository.findByProductIdForUpdate(reservation.getProductId())
                .orElseThrow(() -> new NotFoundException("Inventory not found"));
        inventory.release(reservation.getQuantity());
        reservation.release();

        movementRepository.save(new InventoryMovement(reservation.getProductId(),
                reservation.getId(), MovementType.RELEASED, reservation.getQuantity(),
                "Reservation released"));

        outboxService.add(reservation.getOrderId(), "ReservationReleased", correlationId,
                Map.of("reservationId", reservation.getId(), "orderId", reservation.getOrderId(),
                        "productId", reservation.getProductId(), "quantity", reservation.getQuantity()));

        return toReservationResponse(reservation, "Reservation released");
    }

    @Transactional(readOnly = true)
    public ReservationResponse getReservation(UUID reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation not found"));
        return toReservationResponse(reservation, "Reservation retrieved");
    }

    @Transactional
    public void expireReservations() {
        var expiredIds = reservationRepository.findExpiredIds(
                ReservationStatus.ACTIVE, Instant.now(), PageRequest.of(0, 100));

        for (UUID id : expiredIds) {
            Reservation reservation = reservationRepository.findByIdForUpdate(id).orElse(null);
            if (reservation == null || reservation.getStatus() != ReservationStatus.ACTIVE
                    || reservation.getExpiresAt().isAfter(Instant.now())) {
                continue;
            }

            Inventory inventory = inventoryRepository.findByProductIdForUpdate(reservation.getProductId())
                    .orElseThrow(() -> new IllegalStateException("Inventory missing for reservation"));
            inventory.release(reservation.getQuantity());
            reservation.expire();

            movementRepository.save(new InventoryMovement(reservation.getProductId(),
                    reservation.getId(), MovementType.EXPIRED, reservation.getQuantity(),
                    "Reservation TTL elapsed"));
            outboxService.add(reservation.getOrderId(), "ReservationExpired", null,
                    Map.of("reservationId", reservation.getId(), "orderId", reservation.getOrderId(),
                            "productId", reservation.getProductId(), "quantity", reservation.getQuantity()));
        }
    }

    private ProductResponse toProductResponse(Product product, Inventory inventory) {
        return new ProductResponse(product.getId(), product.getSku(), product.getName(),
                product.isActive(), inventory.getTotalStock(), inventory.getAvailableStock(),
                inventory.getReservedStock(), product.getCreatedAt());
    }

    private StockResponse toStockResponse(Inventory inventory) {
        return new StockResponse(inventory.getProductId(), inventory.getTotalStock(),
                inventory.getAvailableStock(), inventory.getReservedStock(), inventory.getVersion());
    }

    private ReservationResponse toReservationResponse(Reservation reservation, String message) {
        return new ReservationResponse(reservation.getId(), reservation.getOrderId(),
                reservation.getProductId(), reservation.getQuantity(), reservation.getStatus(),
                reservation.getExpiresAt(), message);
    }
}
