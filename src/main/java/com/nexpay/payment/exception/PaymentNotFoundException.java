package com.nexpay.payment.exception;

import java.util.UUID;

public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(UUID id) {
        super("payment not found with id: " + id);
    }

    public PaymentNotFoundException(String field, String value) {
        super("payment not found with " + field + ": " + value);
    }
}