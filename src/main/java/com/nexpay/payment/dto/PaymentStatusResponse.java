package com.nexpay.payment.dto;

import com.nexpay.payment.model.Payment;
import com.nexpay.payment.model.PaymentGateway;
import com.nexpay.payment.model.PaymentStatus;
import com.nexpay.payment.model.PaymentType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class PaymentStatusResponse {

    private UUID           id;
    private UUID           userId;
    private BigDecimal     amount;
    private String         currency;
    private PaymentGateway gateway;
    private PaymentType    type;
    private PaymentStatus  status;
    private String         gatewayRef;
    private String         failureReason;
    private Instant        initiatedAt;
    private Instant        confirmedAt;
    private Instant        failedAt;
    private Instant        expiresAt;

    public static PaymentStatusResponse from(Payment payment) {
        return PaymentStatusResponse.builder()
                .id(payment.getId())
                .userId(payment.getUserId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .gateway(payment.getGateway())
                .type(payment.getType())
                .status(payment.getStatus())
                .gatewayRef(payment.getGatewayRef())
                .failureReason(payment.getFailureReason())
                .initiatedAt(payment.getInitiatedAt())
                .confirmedAt(payment.getConfirmedAt())
                .failedAt(payment.getFailedAt())
                .expiresAt(payment.getExpiresAt())
                .build();
    }
}