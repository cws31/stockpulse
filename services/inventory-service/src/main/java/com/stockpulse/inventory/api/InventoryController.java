package com.stockpulse.inventory.api;

import com.stockpulse.inventory.dto.*;
import com.stockpulse.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/inventory")
@Tag(name = "Inventory", description = "Manage products, stock levels, and reservations")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/products")
    @Operation(summary = "Create a product", description = "Creates an active product and its inventory record. Initial stock may be zero but cannot be negative.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Product and inventory created", content = @Content(schema = @Schema(implementation = ProductResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid product details"),
            @ApiResponse(responseCode = "409", description = "SKU already exists")
    })
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inventoryService.createProduct(request));
    }

    @GetMapping("/products/{productId}")
    @Operation(summary = "Get product details", description = "Returns product information together with its current stock balances.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product found", content = @Content(schema = @Schema(implementation = ProductResponse.class))),
            @ApiResponse(responseCode = "404", description = "Product or inventory does not exist")
    })
    public ProductResponse getProduct(@Parameter(description = "Product UUID", required = true) @PathVariable UUID productId) {
        return inventoryService.getProduct(productId);
    }

    @GetMapping("/products/{productId}/stock")
    @Operation(summary = "Get stock balance", description = "Returns total, available, and reserved quantities. The invariant is total = available + reserved.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock balance returned", content = @Content(schema = @Schema(implementation = StockResponse.class))),
            @ApiResponse(responseCode = "404", description = "Inventory does not exist")
    })
    public StockResponse getStock(@Parameter(description = "Product UUID", required = true) @PathVariable UUID productId) {
        return inventoryService.getStock(productId);
    }

    @PostMapping("/stock/adjustments")
    @Operation(summary = "Adjust available stock", description = "Adds or removes stock. Positive quantities add stock. Negative quantities remove available stock only; reserved stock cannot be removed. Zero is rejected.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock adjusted", content = @Content(schema = @Schema(implementation = StockResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Inventory does not exist"),
            @ApiResponse(responseCode = "409", description = "Adjustment violates stock constraints")
    })
    public StockResponse adjustStock(@Valid @RequestBody StockAdjustmentRequest request) {
        return inventoryService.adjustStock(request);
    }

    @PostMapping("/reservations")
    @Operation(summary = "Reserve inventory for an order", description = "Creates a time-limited reservation. A successful reservation moves quantity from available to reserved stock. Repeating the same request with the same idempotency key returns the existing reservation without deducting stock again. Insufficient stock returns HTTP 409 and does not create a reservation.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Inventory reserved", content = @Content(schema = @Schema(implementation = ReservationResponse.class), examples = @ExampleObject(value = "{\"reservationId\":\"81bc774e-bee8-4f77-9e5d-31c8e970b92a\",\"orderId\":\"ORDER-001\",\"productId\":\"8016e731-35ec-488a-9778-31817c8ccaea\",\"quantity\":3,\"status\":\"ACTIVE\",\"expiresAt\":\"2026-11-08T08:51:46Z\",\"message\":\"Inventory reserved\"}"))),
            @ApiResponse(responseCode = "400", description = "Missing or invalid fields"),
            @ApiResponse(responseCode = "404", description = "Product inventory does not exist or product is inactive"),
            @ApiResponse(responseCode = "409", description = "Insufficient stock or idempotency key conflict", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<?> reserve(
            @Valid @RequestBody ReservationRequest request,
            @Parameter(name = "X-Correlation-Id", description = "Optional ID used to trace this request across services", in = ParameterIn.HEADER)
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        ReservationResponse response = inventoryService.reserve(request, correlationId);
        if (response.reservationId() == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiErrorResponse(
                    java.time.Instant.now(), HttpStatus.CONFLICT.value(),
                    HttpStatus.CONFLICT.getReasonPhrase(), response.message()));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/reservations/{reservationId}/confirm")
    @Operation(summary = "Confirm a reservation", description = "Consumes reserved stock and permanently reduces total stock. Confirmation is idempotent when the reservation is already confirmed. Expired reservations are expired and their stock is released instead.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reservation confirmed or already confirmed", content = @Content(schema = @Schema(implementation = ReservationResponse.class))),
            @ApiResponse(responseCode = "404", description = "Reservation does not exist"),
            @ApiResponse(responseCode = "409", description = "Reservation is released or expired")
    })
    public ReservationResponse confirm(
            @Parameter(description = "Reservation UUID", required = true) @PathVariable UUID reservationId,
            @Parameter(name = "X-Correlation-Id", description = "Optional distributed trace correlation ID", in = ParameterIn.HEADER)
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return inventoryService.confirm(reservationId, correlationId);
    }

    @PostMapping("/reservations/{reservationId}/release")
    @Operation(summary = "Release a reservation", description = "Returns reserved quantity to available stock. Releasing an already released or expired reservation is safe and does not change stock again. Confirmed reservations cannot be released.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reservation released or already released", content = @Content(schema = @Schema(implementation = ReservationResponse.class))),
            @ApiResponse(responseCode = "404", description = "Reservation does not exist"),
            @ApiResponse(responseCode = "409", description = "Confirmed reservation cannot be released")
    })
    public ReservationResponse release(
            @Parameter(description = "Reservation UUID", required = true) @PathVariable UUID reservationId,
            @Parameter(name = "X-Correlation-Id", description = "Optional distributed trace correlation ID", in = ParameterIn.HEADER)
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return inventoryService.release(reservationId, correlationId);
    }

    @GetMapping("/reservations/{reservationId}")
    @Operation(summary = "Get reservation details", description = "Returns the current state, quantity, and expiry time for a reservation.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reservation found", content = @Content(schema = @Schema(implementation = ReservationResponse.class))),
            @ApiResponse(responseCode = "404", description = "Reservation does not exist")
    })
    public ReservationResponse getReservation(@Parameter(description = "Reservation UUID", required = true) @PathVariable UUID reservationId) {
        return inventoryService.getReservation(reservationId);
    }
}
