package com.nexpay.payment.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexpay.payment.model.PaymentOutboxEvent;
import com.nexpay.payment.repository.PaymentOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentOutboxPoller {

    private final PaymentOutboxRepository        paymentOutboxRepository;
    private final KafkaTemplate<String, String>  kafkaTemplate;
    private final ObjectMapper                   objectMapper;

    private static final int BATCH_SIZE = 10;

    // ─────────────────────────────────────────
    // POLL EVERY 5 SECONDS
    // ─────────────────────────────────────────
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void poll() {

        List<PaymentOutboxEvent> pendingEvents = paymentOutboxRepository
                .findPendingEventsWithLockAndLimit(BATCH_SIZE);

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("payment outbox poller found {} pending events",
                pendingEvents.size());

        for (PaymentOutboxEvent event : pendingEvents) {
            process(event);
        }
    }

    // ─────────────────────────────────────────
    // PROCESS SINGLE EVENT
    // ─────────────────────────────────────────
    private void process(PaymentOutboxEvent event) {
        try {
            // serialize payload to JSON string
            String payload = objectMapper.writeValueAsString(event.getPayload());

            // topic = event type
            // topup.confirmed → Wallet Service consumes
            // topup.failed    → Notification Service consumes
            // key = paymentId → ordering per payment
            kafkaTemplate.send(
                    event.getEventType(),
                    event.getPaymentId().toString(),
                    payload
            ).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("failed to publish payment event id: {} type: {} error: {}",
                            event.getId(), event.getEventType(), ex.getMessage());
                    paymentOutboxRepository.markAsFailed(event.getId());
                } else {
                    log.info("published payment event id: {} type: {} topic: {}",
                            event.getId(), event.getEventType(),
                            result.getRecordMetadata().topic());
                    paymentOutboxRepository.markAsPublished(
                            event.getId(),
                            Instant.now()
                    );
                }
            });

        } catch (Exception ex) {
            log.error("error processing payment outbox event id: {} error: {}",
                    event.getId(), ex.getMessage());
            paymentOutboxRepository.markAsFailed(event.getId());
        }
    }
}