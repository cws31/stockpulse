package com.stockpulse.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Details required to create a product and initialize its stock")
public record CreateProductRequest(
        @Schema(description = "Unique stock keeping unit", example = "LAPTOP-001", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 80) String sku,
        @Schema(description = "Human-readable product name", example = "Laptop", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 200) String name,
        @Schema(description = "Starting quantity. Zero is allowed; negative values are not.", example = "10", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED)
        @Min(0) long initialStock
) {
}
