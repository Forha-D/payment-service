package com.nexpay.payment.exception;

public class PaymentAlreadyExistsException extends RuntimeException {

    public PaymentAlreadyExistsException(String idempotencyKey) {
        super("payment already exists with idempotency key: " + idempotencyKey);
    }
}