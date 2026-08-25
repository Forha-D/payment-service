package com.nexpay.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "payment.gateway")
public class GatewayProperties {

    private Nagad  nagad  = new Nagad();
    private Bkash  bkash  = new Bkash();
    private Stripe stripe = new Stripe();
    private Card   card   = new Card();

    @Data
    public static class Nagad {
        private String baseUrl;
        private String merchantId;
        private String secretKey;
    }

    @Data
    public static class Bkash {
        private String baseUrl;
        private String appKey;
        private String appSecret;
    }

    @Data
    public static class Stripe {
        private String secretKey;
        private String webhookSecret;
    }

    @Data
    public static class Card {
        private String baseUrl;
        private String merchantId;
        private String secretKey;
    }
}