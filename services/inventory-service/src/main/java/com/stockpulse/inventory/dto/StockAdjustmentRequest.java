package com.stockpulse.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(description = "Manual stock correction. Positive quantity adds stock; negative quantity removes available stock.")
public record StockAdjustmentRequest(
        @Schema(description = "Product UUID", example = "8016e731-35ec-488a-9778-31817c8ccaea", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull UUID productId,
        @Schema(description = "Signed adjustment quantity. Must not be zero; negative adjustments cannot consume reserved stock.", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
        long quantity,
        @Schema(description = "Reason for the audit trail", example = "New stock received", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 250) String reason
) {
}
