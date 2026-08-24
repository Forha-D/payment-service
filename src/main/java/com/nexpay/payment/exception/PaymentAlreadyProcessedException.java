package com.nexpay.payment.exception;

import java.util.UUID;

public class PaymentAlreadyProcessedException extends RuntimeException {

    public PaymentAlreadyProcessedException(UUID paymentId) {
        super("payment already processed: " + paymentId);
    }
}