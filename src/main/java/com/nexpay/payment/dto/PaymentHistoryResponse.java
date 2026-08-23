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
public class PaymentHistoryResponse {

    private UUID           id;
    private BigDecimal     amount;
    private String         currency;
    private PaymentGateway gateway;
    private PaymentType    type;
    private PaymentStatus  status;
    private String         gatewayRef;
    private Instant        initiatedAt;
    private Instant        confirmedAt;

    public static PaymentHistoryResponse from(Payment payment) {
        return PaymentHistoryResponse.builder()
                .id(payment.getId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .gateway(payment.getGateway())
                .type(payment.getType())
                .status(payment.getStatus())
                .gatewayRef(payment.getGatewayRef())
                .initiatedAt(payment.getInitiatedAt())
                .confirmedAt(payment.getConfirmedAt())
                .build();
    }
}