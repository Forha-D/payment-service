package com.nexpay.payment.service.gateway;

import com.nexpay.payment.model.Payment;

import java.util.Map;

public interface PaymentGatewayService {

    // initiate payment — returns payment URL and gateway token
    GatewayInitiateResponse initiate(Payment payment, String webhookUrl);

    // verify webhook signature — throws WebhookVerificationException if invalid
    void verifyWebhook(Map<String, Object> payload, String signature);

    // extract gateway reference from webhook payload
    String extractGatewayRef(Map<String, Object> payload);

    // extract payment status from webhook payload
    String extractStatus(Map<String, Object> payload);
}