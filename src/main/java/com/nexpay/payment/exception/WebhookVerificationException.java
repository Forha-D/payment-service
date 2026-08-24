package com.nexpay.payment.exception;

public class WebhookVerificationException extends RuntimeException {

    public WebhookVerificationException(String gateway) {
        super("webhook signature verification failed for gateway: " + gateway);
    }

    public WebhookVerificationException(String gateway, String reason) {
        super("webhook verification failed for gateway: " + gateway + " reason: " + reason);
    }
}