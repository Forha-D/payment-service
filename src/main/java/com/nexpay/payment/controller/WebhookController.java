package com.nexpay.payment.controller;

import com.nexpay.payment.model.PaymentGateway;
import com.nexpay.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/payment/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final PaymentService paymentService;

    // ─────────────────────────────────────────
    // NAGAD WEBHOOK
    // POST /payment/webhook/nagad
    // No JWT — Nagad calls this directly
    // ─────────────────────────────────────────
    @PostMapping("/nagad")
    public ResponseEntity<Map<String, String>> nagadWebhook(
            @RequestBody  Map<String, Object> payload,
            @RequestHeader(value = "X-Nagad-Signature", required = false)
            String signature
    ) {
        log.info("nagad webhook received");

        paymentService.handleWebhook(
                PaymentGateway.NAGAD,
                payload,
                signature
        );

        return ResponseEntity.ok(Map.of("status", "received"));
    }

    // ─────────────────────────────────────────
    // BKASH WEBHOOK
    // POST /payment/webhook/bkash
    // No JWT — bKash calls this directly
    // ─────────────────────────────────────────
    @PostMapping("/bkash")
    public ResponseEntity<Map<String, String>> bkashWebhook(
            @RequestBody  Map<String, Object> payload,
            @RequestHeader(value = "X-Bkash-Signature", required = false)
            String signature
    ) {
        log.info("bkash webhook received");

        paymentService.handleWebhook(
                PaymentGateway.BKASH,
                payload,
                signature
        );

        return ResponseEntity.ok(Map.of("status", "received"));
    }

    // ─────────────────────────────────────────
    // STRIPE WEBHOOK
    // POST /payment/webhook/stripe
    // No JWT — Stripe calls this directly
    // ─────────────────────────────────────────
    @PostMapping("/stripe")
    public ResponseEntity<Map<String, String>> stripeWebhook(
            @RequestBody  Map<String, Object> payload,
            @RequestHeader(value = "Stripe-Signature", required = false)
            String signature
    ) {
        log.info("stripe webhook received");

        paymentService.handleWebhook(
                PaymentGateway.STRIPE,
                payload,
                signature
        );

        return ResponseEntity.ok(Map.of("status", "received"));
    }

    // ─────────────────────────────────────────
    // CARD WEBHOOK (SSL Commerz)
    // POST /payment/webhook/card
    // No JWT — SSL Commerz calls this directly
    // ─────────────────────────────────────────
    @PostMapping("/card")
    public ResponseEntity<Map<String, String>> cardWebhook(
            @RequestBody  Map<String, Object> payload,
            @RequestHeader(value = "X-Card-Signature", required = false)
            String signature
    ) {
        log.info("ssl commerz webhook received");

        paymentService.handleWebhook(
                PaymentGateway.CARD,
                payload,
                signature
        );

        return ResponseEntity.ok(Map.of("status", "received"));
    }
}