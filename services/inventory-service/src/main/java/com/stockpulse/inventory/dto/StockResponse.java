package com.stockpulse.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Current inventory balance for a product")
public record StockResponse(
        @Schema(description = "Product UUID") UUID productId,
        @Schema(description = "Total physical stock: available plus reserved", example = "10") long totalStock,
        @Schema(description = "Stock available to reserve", example = "7") long availableStock,
        @Schema(description = "Stock currently held by reservations", example = "3") long reservedStock,
        @Schema(description = "Optimistic-lock version incremented by stock mutations", example = "2") long version
) {
}
