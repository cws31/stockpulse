package com.stockpulse.inventory.dto;

import com.stockpulse.inventory.domain.ReservationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Reservation details and its current lifecycle status")
public record ReservationResponse(
        @Schema(description = "Generated reservation UUID") UUID reservationId,
        @Schema(description = "Order identifier", example = "ORDER-1001") String orderId,
        @Schema(description = "Product UUID") UUID productId,
        @Schema(description = "Reserved quantity", example = "2") long quantity,
        @Schema(description = "ACTIVE, CONFIRMED, RELEASED, or EXPIRED") ReservationStatus status,
        @Schema(description = "Reservation expiry timestamp in UTC") Instant expiresAt,
        @Schema(description = "Human-readable operation result", example = "Inventory reserved") String message
) {
}
