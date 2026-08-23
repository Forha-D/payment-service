package com.nexpay.payment.repository;

import com.nexpay.payment.model.OutboxStatus;
import com.nexpay.payment.model.PaymentOutboxEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentOutboxRepository extends JpaRepository<PaymentOutboxEvent, UUID> {

    // fetch pending events with lock and limit — for outbox poller
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = """
            SELECT * FROM payment_outbox
            WHERE status = 'PENDING'
            ORDER BY created_at ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<PaymentOutboxEvent> findPendingEventsWithLockAndLimit(
            @Param("limit") int limit
    );

    // mark as published after successful Kafka publish
    @Modifying
    @Query("""
            UPDATE PaymentOutboxEvent o
            SET o.status      = 'PUBLISHED',
                o.publishedAt = :publishedAt
            WHERE o.id = :id
            """)
    void markAsPublished(
            @Param("id")          UUID    id,
            @Param("publishedAt") Instant publishedAt
    );

    // mark as failed after retries exhausted
    @Modifying
    @Query("""
            UPDATE PaymentOutboxEvent o
            SET o.status = 'FAILED'
            WHERE o.id = :id
            """)
    void markAsFailed(@Param("id") UUID id);

    // find by payment ID — for debugging
    List<PaymentOutboxEvent> findByPaymentIdOrderByCreatedAtDesc(UUID paymentId);

    // count pending — for health check
    long countByStatus(OutboxStatus status);

    // find failed events — for monitoring
    List<PaymentOutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxStatus status);
}