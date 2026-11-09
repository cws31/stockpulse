package com.stockpulse.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(description = "Idempotent request to reserve inventory for an order")
public record ReservationRequest(
        @Schema(description = "Unique order identifier from the Order Service", example = "ORDER-1001", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 100) String orderId,
        @Schema(description = "UUID of an active product", example = "8016e731-35ec-488a-9778-31817c8ccaea", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull UUID productId,
        @Schema(description = "Number of units to reserve; must be at least one", example = "2", minimum = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @Min(1) long quantity,
        @Schema(description = "Unique key for safe retries. Reusing a key with a different payload returns 409.", example = "ORDER-1001-PRODUCT-001", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 150) String idempotencyKey
) {
}
