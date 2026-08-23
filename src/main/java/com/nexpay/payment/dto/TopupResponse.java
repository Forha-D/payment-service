package com.nexpay.payment.dto;

import com.nexpay.payment.model.PaymentGateway;
import com.nexpay.payment.model.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class TopupResponse {

    private UUID          id;
    private UUID          userId;
    private BigDecimal    amount;
    private String        currency;
    private PaymentGateway gateway;
    private PaymentStatus  status;
    private String         paymentUrl;   // Flutter opens this URL
    private Instant        expiresAt;
    private Instant        initiatedAt;
}