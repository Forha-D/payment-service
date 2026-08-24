package com.nexpay.payment.exception;

import java.util.UUID;

public class PaymentExpiredException extends RuntimeException {

    public PaymentExpiredException(UUID paymentId) {
        super("payment expired: " + paymentId);
    }
}