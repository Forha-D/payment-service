package com.nexpay.payment.repository;

import com.nexpay.payment.model.Payment;
import com.nexpay.payment.model.PaymentGateway;
import com.nexpay.payment.model.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    // idempotency check — before every payment initiation
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    // find by gateway reference — used in webhook processing
    Optional<Payment> findByGatewayRef(String gatewayRef);

    // find by gateway reference with lock — used when updating status
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.gatewayRef = :gatewayRef")
    Optional<Payment> findByGatewayRefWithLock(@Param("gatewayRef") String gatewayRef);

    // find by id with lock — used when updating payment status
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdWithLock(@Param("id") UUID id);

    // paginated payment history for user
    Page<Payment> findByUserIdOrderByInitiatedAtDesc(
            UUID userId,
            Pageable pageable
    );

    // filter by status
    Page<Payment> findByUserIdAndStatusOrderByInitiatedAtDesc(
            UUID userId,
            PaymentStatus status,
            Pageable pageable
    );

    // filter by gateway
    Page<Payment> findByUserIdAndGatewayOrderByInitiatedAtDesc(
            UUID userId,
            PaymentGateway gateway,
            Pageable pageable
    );

    // find expired pending payments — for background cleanup job
    @Query("""
            SELECT p FROM Payment p
            WHERE p.status = 'PENDING'
            AND p.expiresAt < :now
            """)
    List<Payment> findExpiredPendingPayments(@Param("now") Instant now);

    // mark payment as success
    @Modifying
    @Query("""
            UPDATE Payment p
            SET p.status      = 'SUCCESS',
                p.gatewayRef  = :gatewayRef,
                p.confirmedAt = :confirmedAt,
                p.updatedAt   = :updatedAt
            WHERE p.id = :id
            """)
    void markAsSuccess(
            @Param("id")          UUID    id,
            @Param("gatewayRef")  String  gatewayRef,
            @Param("confirmedAt") Instant confirmedAt,
            @Param("updatedAt")   Instant updatedAt
    );

    // mark payment as failed
    @Modifying
    @Query("""
            UPDATE Payment p
            SET p.status        = 'FAILED',
                p.failureReason = :reason,
                p.failedAt      = :failedAt,
                p.updatedAt     = :updatedAt
            WHERE p.id = :id
            """)
    void markAsFailed(
            @Param("id")       UUID    id,
            @Param("reason")   String  reason,
            @Param("failedAt") Instant failedAt,
            @Param("updatedAt")Instant updatedAt
    );

    // mark payment as expired
    @Modifying
    @Query("""
            UPDATE Payment p
            SET p.status    = 'EXPIRED',
                p.updatedAt = :updatedAt
            WHERE p.id = :id
            """)
    void markAsExpired(
            @Param("id")       UUID    id,
            @Param("updatedAt")Instant updatedAt
    );

    // idempotency existence check
    boolean existsByIdempotencyKey(String idempotencyKey);

    // count by status — for health check and monitoring
    long countByStatus(PaymentStatus status);
}