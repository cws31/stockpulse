package com.stockpulse.inventory.api;

import com.stockpulse.inventory.dto.ApiErrorResponse;
import com.stockpulse.inventory.service.ConflictException;
import com.stockpulse.inventory.service.NotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler({ ConflictException.class, DataIntegrityViolationException.class })
    public ResponseEntity<ApiErrorResponse> handleConflict(Exception exception) {
        String message = exception instanceof ConflictException
                ? exception.getMessage()
                : "A conflicting record already exists";
        return response(HttpStatus.CONFLICT, message);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = "Request validation failed";
        }
        return response(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(IllegalArgumentException exception) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return response(HttpStatus.BAD_REQUEST, "Request body is missing or contains invalid JSON or field values");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return response(HttpStatus.BAD_REQUEST, "Invalid value for '" + exception.getName() + "'");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception) {
        return response(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method is not supported for this endpoint");
    }

    private ResponseEntity<ApiErrorResponse> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), message));
    }
}
