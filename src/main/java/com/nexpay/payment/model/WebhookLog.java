package com.nexpay.payment.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "webhook_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "gateway", nullable = false, length = 20)
    private PaymentGateway gateway;

    // raw webhook body — never lose this, critical for disputes
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> rawPayload;

    // signature from gateway header
    @Column(name = "signature", length = 500)
    private String signature;

    // was signature verified successfully
    @Column(name = "verified", nullable = false)
    private boolean verified;

    // what action was taken after verification
    @Column(name = "action_taken", length = 100)
    private String actionTaken;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @PrePersist
    protected void onCreate() {
        receivedAt = Instant.now();
    }
}