package com.nexpay.payment.controller;

import com.nexpay.payment.dto.*;
import com.nexpay.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    // ─────────────────────────────────────────
    // INITIATE TOP-UP
    // POST /payment/topup
    // ─────────────────────────────────────────
    @PostMapping("/topup")
    public ResponseEntity<ApiResponse<TopupResponse>> initiateTopup(
            @RequestHeader("X-User-ID") String userId,
            @Valid @RequestBody TopupRequest req
    ) {
        log.info("topup request userId: {} amount: {} gateway: {}",
                userId, req.getAmount(), req.getGateway());

        TopupResponse response = paymentService.initiateTopup(
                UUID.fromString(userId),
                req
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("payment initiated", response));
    }

    // ─────────────────────────────────────────
    // GET PAYMENT STATUS
    // GET /payment/status/{paymentId}
    // ─────────────────────────────────────────
    @GetMapping("/status/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> getPaymentStatus(
            @RequestHeader("X-User-ID") String userId,
            @PathVariable UUID paymentId
    ) {
        log.info("payment status request userId: {} paymentId: {}",
                userId, paymentId);

        PaymentStatusResponse response = paymentService.getPaymentStatus(
                UUID.fromString(userId),
                paymentId
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ─────────────────────────────────────────
    // GET PAYMENT HISTORY
    // GET /payment/history?page=0&size=20
    // ─────────────────────────────────────────
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<PageResponse<PaymentHistoryResponse>>> getHistory(
            @RequestHeader("X-User-ID") String userId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("payment history request userId: {} page: {} size: {}",
                userId, page, size);

        // cap page size
        int safeSize = Math.min(size, 100);

        Pageable pageable = PageRequest.of(page, safeSize);

        PageResponse<PaymentHistoryResponse> response = paymentService
                .getPaymentHistory(UUID.fromString(userId), pageable);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}