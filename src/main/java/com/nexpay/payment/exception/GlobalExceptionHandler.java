package com.nexpay.payment.exception;

import com.nexpay.payment.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 404 — payment not found
    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handlePaymentNotFound(
            PaymentNotFoundException ex) {
        log.warn("payment not found: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    // 409 — payment already exists
    @ExceptionHandler(PaymentAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handlePaymentAlreadyExists(
            PaymentAlreadyExistsException ex) {
        log.warn("payment already exists: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

    // 409 — payment already processed
    @ExceptionHandler(PaymentAlreadyProcessedException.class)
    public ResponseEntity<ApiResponse<Void>> handlePaymentAlreadyProcessed(
            PaymentAlreadyProcessedException ex) {
        log.warn("payment already processed: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

    // 400 — webhook verification failed
    @ExceptionHandler(WebhookVerificationException.class)
    public ResponseEntity<ApiResponse<Void>> handleWebhookVerification(
            WebhookVerificationException ex) {
        log.error("webhook verification failed: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    // 410 — payment expired
    @ExceptionHandler(PaymentExpiredException.class)
    public ResponseEntity<ApiResponse<Void>> handlePaymentExpired(
            PaymentExpiredException ex) {
        log.warn("payment expired: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.GONE)
                .body(ApiResponse.error(ex.getMessage()));
    }

    // 502 — gateway error
    @ExceptionHandler(GatewayException.class)
    public ResponseEntity<ApiResponse<Void>> handleGatewayError(
            GatewayException ex) {
        log.error("gateway error: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error("payment gateway error — please try again"));
    }

    // 400 — validation errors
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult()
                .getAllErrors()
                .forEach(error -> {
                    String field   = ((FieldError) error).getField();
                    String message = error.getDefaultMessage();
                    errors.put(field, message);
                });
        log.warn("validation failed: {}", errors);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Map<String, String>>builder()
                        .success(false)
                        .message("validation failed")
                        .data(errors)
                        .build());
    }

    // 500 — catch all
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        log.error("unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("internal server error"));
    }
}