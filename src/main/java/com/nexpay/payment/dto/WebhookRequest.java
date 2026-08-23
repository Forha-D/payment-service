package com.nexpay.payment.dto;

import lombok.Data;

import java.util.Map;

@Data
public class WebhookRequest {

    // raw webhook payload — stored as-is for audit
    private Map<String, Object> payload;

    // gateway signature from header
    private String signature;

    // gateway name
    private String gateway;
}