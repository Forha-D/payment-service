package com.nexpay.payment.service;

//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.nexpay.payment.config.GatewayProperties;
import com.nexpay.payment.dto.*;
import com.nexpay.payment.exception.*;
import com.nexpay.payment.model.*;
import com.nexpay.payment.repository.*;
import com.nexpay.payment.service.gateway.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository        paymentRepository;
    private final WebhookLogRepository     webhookLogRepository;
    private final PaymentOutboxRepository  paymentOutboxRepository;
    private final GatewayServiceFactory    gatewayServiceFactory;
    //private final ObjectMapper             objectMapper;

    @Value("${payment.webhook.base-url}")
    private String webhookBaseUrl;

    // ─────────────────────────────────────────
    // INITIATE TOP-UP
    // ─────────────────────────────────────────
    @Transactional
    public TopupResponse initiateTopup(UUID userId, TopupRequest req) {

        // STEP 1: idempotency check
        if (paymentRepository.existsByIdempotencyKey(req.getIdempotencyKey())) {
            log.warn("duplicate topup detected idempotencyKey: {}",
                    req.getIdempotencyKey());

            Payment existing = paymentRepository
                    .findByIdempotencyKey(req.getIdempotencyKey())
                    .orElseThrow(() -> new PaymentNotFoundException(
                            "idempotencyKey", req.getIdempotencyKey()
                    ));

            return buildTopupResponse(existing);
        }

        // STEP 2: create payment record with INITIATED status
        Payment payment = Payment.builder()
                .userId(userId)
                .amount(req.getAmount())
                .currency("BDT")
                .gateway(req.getGateway())
                .type(PaymentType.TOPUP)
                .status(PaymentStatus.INITIATED)
                .idempotencyKey(req.getIdempotencyKey())
                .build();

        payment = paymentRepository.save(payment);

        log.info("payment record created paymentId: {} userId: {} gateway: {}",
                payment.getId(), userId, req.getGateway());

        try {
            // STEP 3: resolve gateway service
            PaymentGatewayService gatewayService =
                    gatewayServiceFactory.resolve(req.getGateway());

            // STEP 4: build webhook URL for this gateway
            String webhookUrl = webhookBaseUrl
                    + "/payment/webhook/"
                    + req.getGateway().name().toLowerCase();

            // STEP 5: call gateway — initiate payment
            GatewayInitiateResponse gatewayResponse =
                    gatewayService.initiate(payment, webhookUrl);

            // STEP 6: update payment with gateway response
            payment.setPaymentUrl(gatewayResponse.getPaymentUrl());
            payment.setGatewayToken(gatewayResponse.getGatewayToken());
            payment.setGatewayRef(gatewayResponse.getGatewayRef());
            payment.setStatus(PaymentStatus.PENDING);
            payment = paymentRepository.save(payment);

            log.info("payment initiated paymentId: {} status: PENDING gateway: {}",
                    payment.getId(), req.getGateway());

            return buildTopupResponse(payment);

        } catch (GatewayException ex) {
            // gateway failed → mark payment as FAILED
            log.error("gateway failed for paymentId: {} error: {}",
                    payment.getId(), ex.getMessage());

            paymentRepository.markAsFailed(
                    payment.getId(),
                    ex.getMessage(),
                    Instant.now(),
                    Instant.now()
            );

            throw ex;
        }
    }

    // ─────────────────────────────────────────
    // HANDLE WEBHOOK
    // called by WebhookController per gateway
    // ─────────────────────────────────────────
    @Transactional
    public void handleWebhook(
            PaymentGateway        gateway,
            Map<String, Object>   payload,
            String                signature
    ) {
        log.info("webhook received from gateway: {}", gateway);

        // STEP 1: log raw webhook — never lose this
        WebhookLog webhookLog = WebhookLog.builder()
                .gateway(gateway)
                .rawPayload(payload)
                .signature(signature)
                .verified(false)
                .build();

        webhookLog = webhookLogRepository.save(webhookLog);

        try {
            // STEP 2: resolve gateway service
            PaymentGatewayService gatewayService =
                    gatewayServiceFactory.resolve(gateway);

            // STEP 3: verify webhook signature
            gatewayService.verifyWebhook(payload, signature);

            // STEP 4: mark webhook as verified
            webhookLog.setVerified(true);

            // STEP 5: extract gateway reference and status
            String gatewayRef = gatewayService.extractGatewayRef(payload);
            String status     = gatewayService.extractStatus(payload);

            log.info("webhook verified gateway: {} gatewayRef: {} status: {}",
                    gateway, gatewayRef, status);

            // STEP 6: find payment by gateway ref with lock
            Payment payment = paymentRepository
                    .findByGatewayRefWithLock(gatewayRef)
                    .orElseThrow(() -> new PaymentNotFoundException(
                            "gatewayRef", gatewayRef
                    ));

            // STEP 7: check if already processed — idempotency
            if (payment.getStatus() == PaymentStatus.SUCCESS ||
                payment.getStatus() == PaymentStatus.FAILED) {
                log.warn("payment already processed paymentId: {} status: {}",
                        payment.getId(), payment.getStatus());
                webhookLog.setActionTaken("ALREADY_PROCESSED");
                webhookLogRepository.save(webhookLog);
                return;
            }

            // STEP 8: update payment and write outbox — atomic
            if ("SUCCESS".equals(status)) {
                handlePaymentSuccess(payment, gatewayRef, webhookLog);
            } else {
                handlePaymentFailure(payment, "gateway reported failure", webhookLog);
            }

        } catch (WebhookVerificationException ex) {
            log.error("webhook verification failed gateway: {} error: {}",
                    gateway, ex.getMessage());
            webhookLog.setActionTaken("VERIFICATION_FAILED");
            webhookLogRepository.save(webhookLog);
            throw ex;

        } catch (PaymentNotFoundException ex) {
            log.error("payment not found for webhook gateway: {} error: {}",
                    gateway, ex.getMessage());
            webhookLog.setActionTaken("PAYMENT_NOT_FOUND");
            webhookLogRepository.save(webhookLog);
            throw ex;
        }
    }

    // ─────────────────────────────────────────
    // HANDLE PAYMENT SUCCESS
    // ─────────────────────────────────────────
    private void handlePaymentSuccess(
            Payment    payment,
            String     gatewayRef,
            WebhookLog webhookLog
    ) {
        Instant now = Instant.now();

        // update payment status
        paymentRepository.markAsSuccess(
                payment.getId(),
                gatewayRef,
                now,
                now
        );

        // write outbox event — same transaction
        // Wallet Service consumes this to credit balance
        PaymentOutboxEvent outbox = PaymentOutboxEvent.builder()
                .eventType("topup.confirmed")
                .payload(Map.of(
                        "payment_id",  payment.getId().toString(),
                        "user_id",     payment.getUserId().toString(),
                        "amount",      payment.getAmount().toString(),
                        "currency",    payment.getCurrency(),
                        "gateway",     payment.getGateway().name(),
                        "gateway_ref", gatewayRef,
                        "confirmed_at",now.toString()
                ))
                .status(OutboxStatus.PENDING)
                .paymentId(payment.getId())
                .build();

        paymentOutboxRepository.save(outbox);

        // update webhook log
        webhookLog.setPaymentId(payment.getId());
        webhookLog.setActionTaken("TOPUP_CONFIRMED");
        webhookLogRepository.save(webhookLog);

        log.info("payment SUCCESS paymentId: {} userId: {} amount: {} gateway: {}",
                payment.getId(), payment.getUserId(),
                payment.getAmount(), payment.getGateway());
    }

    // ─────────────────────────────────────────
    // HANDLE PAYMENT FAILURE
    // ─────────────────────────────────────────
    private void handlePaymentFailure(
            Payment    payment,
            String     reason,
            WebhookLog webhookLog
    ) {
        Instant now = Instant.now();

        // update payment status
        paymentRepository.markAsFailed(
                payment.getId(),
                reason,
                now,
                now
        );

        // write outbox event — same transaction
        PaymentOutboxEvent outbox = PaymentOutboxEvent.builder()
                .eventType("topup.failed")
                .payload(Map.of(
                        "payment_id", payment.getId().toString(),
                        "user_id",    payment.getUserId().toString(),
                        "amount",     payment.getAmount().toString(),
                        "currency",   payment.getCurrency(),
                        "gateway",    payment.getGateway().name(),
                        "reason",     reason,
                        "failed_at",  now.toString()
                ))
                .status(OutboxStatus.PENDING)
                .paymentId(payment.getId())
                .build();

        paymentOutboxRepository.save(outbox);

        // update webhook log
        webhookLog.setPaymentId(payment.getId());
        webhookLog.setActionTaken("TOPUP_FAILED");
        webhookLogRepository.save(webhookLog);

        log.warn("payment FAILED paymentId: {} userId: {} reason: {}",
                payment.getId(), payment.getUserId(), reason);
    }

    // ─────────────────────────────────────────
    // GET PAYMENT STATUS
    // ─────────────────────────────────────────
    @Transactional(readOnly = true)
    public PaymentStatusResponse getPaymentStatus(UUID userId, UUID paymentId) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        // security — user can only see their own payments
        if (!payment.getUserId().equals(userId)) {
            throw new PaymentNotFoundException(paymentId);
        }

        return PaymentStatusResponse.from(payment);
    }

    // ─────────────────────────────────────────
    // GET PAYMENT HISTORY
    // ─────────────────────────────────────────
    @Transactional(readOnly = true)
    public PageResponse<PaymentHistoryResponse> getPaymentHistory(
            UUID     userId,
            Pageable pageable
    ) {
        Page<Payment> page = paymentRepository
                .findByUserIdOrderByInitiatedAtDesc(userId, pageable);

        return PageResponse.from(page, PaymentHistoryResponse::from);
    }

    // ─────────────────────────────────────────
    // EXPIRE PENDING PAYMENTS — runs every 5 min
    // ─────────────────────────────────────────
    @Scheduled(fixedDelay = 300000)
    @Transactional
    public void expirePendingPayments() {
        List<Payment> expired = paymentRepository
                .findExpiredPendingPayments(Instant.now());

        if (expired.isEmpty()) return;

        log.info("expiring {} pending payments", expired.size());

        expired.forEach(payment -> {
            paymentRepository.markAsExpired(payment.getId(), Instant.now());

            // write topup.failed outbox — notify user
            PaymentOutboxEvent outbox = PaymentOutboxEvent.builder()
                    .eventType("topup.failed")
                    .payload(Map.of(
                            "payment_id", payment.getId().toString(),
                            "user_id",    payment.getUserId().toString(),
                            "amount",     payment.getAmount().toString(),
                            "currency",   payment.getCurrency(),
                            "gateway",    payment.getGateway().name(),
                            "reason",     "payment window expired",
                            "failed_at",  Instant.now().toString()
                    ))
                    .status(OutboxStatus.PENDING)
                    .paymentId(payment.getId())
                    .build();

            paymentOutboxRepository.save(outbox);

            log.warn("payment expired paymentId: {} userId: {}",
                    payment.getId(), payment.getUserId());
        });
    }

    // ─────────────────────────────────────────
    // BUILD TOPUP RESPONSE
    // ─────────────────────────────────────────
    private TopupResponse buildTopupResponse(Payment payment) {
        return TopupResponse.builder()
                .id(payment.getId())
                .userId(payment.getUserId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .gateway(payment.getGateway())
                .status(payment.getStatus())
                .paymentUrl(payment.getPaymentUrl())
                .expiresAt(payment.getExpiresAt())
                .initiatedAt(payment.getInitiatedAt())
                .build();
    }
}