package com.nexpay.payment.dto;

import com.nexpay.payment.model.PaymentGateway;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TopupRequest {

    @NotNull(message = "amount is required")
    @DecimalMin(value = "10.0000", message = "minimum top-up amount is 10 BDT")
    private BigDecimal amount;

    @NotNull(message = "gateway is required")
    private PaymentGateway gateway;

    @NotBlank(message = "idempotency key is required")
    private String idempotencyKey;
}