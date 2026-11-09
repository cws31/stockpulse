package com.stockpulse.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Consistent error response returned when an API request cannot be completed")
public record ApiErrorResponse(
        @Schema(description = "UTC time when the error was generated", example = "2026-11-08T08:46:03Z")
        Instant timestamp,
        @Schema(description = "HTTP status code", example = "409")
        int status,
        @Schema(description = "HTTP status label", example = "Conflict")
        String error,
        @Schema(description = "Readable explanation of the failure", example = "Insufficient available stock")
        String message
) {
}
