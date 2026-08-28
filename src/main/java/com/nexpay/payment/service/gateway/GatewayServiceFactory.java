// src/main/java/com/nexpay/payment/service/gateway/GatewayServiceFactory.java
package com.nexpay.payment.service.gateway;

import com.nexpay.payment.exception.GatewayException;
import com.nexpay.payment.model.PaymentGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayServiceFactory {

    // Spring injects all PaymentGatewayService beans automatically
    // key = bean name, value = the implementation
    private final Map<String, PaymentGatewayService> gatewayServices;

    /**
     * Resolves the correct gateway service by PaymentGateway enum
     *
     * Usage:
     *   gatewayServiceFactory.resolve(PaymentGateway.NAGAD)
     *   → returns NagadGatewayService
     */
    public PaymentGatewayService resolve(PaymentGateway gateway) {
        if (gateway == null) {
            throw new GatewayException("unknown", "Payment gateway must not be null");
        }

        String beanName = getBeanName(gateway);

        PaymentGatewayService service = gatewayServices.get(beanName);

        if (service == null) {
            log.error("no gateway service found for gateway: {}", gateway);
            throw new GatewayException(
                gateway.name(),
                "Unsupported payment gateway"
            );
        }

        log.debug("resolved gateway service: {} for gateway: {}", beanName, gateway);
        return service;
    }

    // maps enum → Spring bean name
    private String getBeanName(PaymentGateway gateway) {
        return switch (gateway) {
            case NAGAD  -> "nagadGatewayService";
            case BKASH  -> "bkashGatewayService";
            case STRIPE -> "stripeGatewayService";
            case CARD   -> "cardGatewayService";
        };
    }
}