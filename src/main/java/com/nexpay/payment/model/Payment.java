package com.nexpay.payment.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "gateway", nullable = false, length = 20)
    private PaymentGateway gateway;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private PaymentType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    // idempotency key — prevents duplicate payment initiation
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    private String idempotencyKey;

    // gateway transaction reference — returned by gateway
    @Column(name = "gateway_ref", length = 255)
    private String gatewayRef;

    // gateway payment URL — returned to client for redirect
    @Column(name = "payment_url", length = 1000)
    private String paymentUrl;

    // gateway token — some gateways return a token on initiation
    @Column(name = "gateway_token", length = 500)
    private String gatewayToken;

    // flexible metadata — store gateway specific fields
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "initiated_at", nullable = false, updatable = false)
    private Instant initiatedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        initiatedAt = Instant.now();
        updatedAt   = Instant.now();
        if (status   == null) status   = PaymentStatus.INITIATED;
        if (currency == null) currency = "BDT";
        if (type     == null) type     = PaymentType.TOPUP;
        // payment expires in 30 minutes if not confirmed
        if (expiresAt == null) expiresAt = Instant.now().plusSeconds(1800);
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}