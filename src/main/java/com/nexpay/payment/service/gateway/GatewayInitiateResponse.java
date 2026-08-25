package com.nexpay.payment.service.gateway;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GatewayInitiateResponse {

    private String paymentUrl;    // Flutter opens this
    private String gatewayToken;  // store for later verification
    private String gatewayRef;    // gateway transaction reference
}