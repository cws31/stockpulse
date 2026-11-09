package com.stockpulse.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Product details together with its current inventory balances")
public record ProductResponse(
        @Schema(description = "Generated product UUID") UUID productId,
        @Schema(description = "Unique stock keeping unit", example = "LAPTOP-001") String sku,
        @Schema(description = "Product display name", example = "Laptop") String name,
        @Schema(description = "Whether the product can receive new reservations") boolean active,
        @Schema(description = "Physical stock quantity, including reserved units", example = "10") long totalStock,
        @Schema(description = "Units currently available for reservation", example = "7") long availableStock,
        @Schema(description = "Units held by active reservations", example = "3") long reservedStock,
        @Schema(description = "Product creation timestamp in UTC") Instant createdAt
) {
}