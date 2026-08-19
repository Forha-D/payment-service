package com.nexpay.payment.model;

public enum PaymentStatus {
    INITIATED,   // payment created, gateway not yet called
    PENDING,     // gateway called, waiting for webhook
    SUCCESS,     // gateway confirmed payment
    FAILED,      // gateway rejected payment
    REFUNDED,    // payment refunded
    EXPIRED      // payment timed out
}