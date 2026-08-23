package com.nexpay.payment.repository;

import com.nexpay.payment.model.PaymentGateway;
import com.nexpay.payment.model.WebhookLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WebhookLogRepository extends JpaRepository<WebhookLog, UUID> {

    // find all webhooks for a payment — for audit
    List<WebhookLog> findByPaymentIdOrderByReceivedAtDesc(UUID paymentId);

    // find unverified webhooks — for monitoring and alerting
    List<WebhookLog> findByVerifiedFalseOrderByReceivedAtDesc();

    // find by gateway — for debugging gateway specific issues
    List<WebhookLog> findByGatewayOrderByReceivedAtDesc(PaymentGateway gateway);

    // count unverified — for health check
    long countByVerifiedFalse();

    // check if webhook already processed — prevent duplicate processing
    @Query("""
            SELECT COUNT(w) > 0 FROM WebhookLog w
            WHERE w.paymentId = :paymentId
            AND w.verified = true
            AND w.actionTaken = :actionTaken
            """)
    boolean existsProcessedWebhook(
            @Param("paymentId")   UUID   paymentId,
            @Param("actionTaken") String actionTaken
    );
}