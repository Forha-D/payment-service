package com.nexpay.payment.service.gateway;

//import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexpay.payment.config.GatewayProperties;
import com.nexpay.payment.exception.GatewayException;
import com.nexpay.payment.exception.WebhookVerificationException;
import com.nexpay.payment.model.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BkashGatewayService implements PaymentGatewayService {

    @Qualifier("bkashWebClient")
    private final WebClient         bkashWebClient;
    private final GatewayProperties gatewayProperties;
    //private final ObjectMapper      objectMapper;

    // ─────────────────────────────────────────
    // INITIATE PAYMENT
    // bKash requires token first, then create payment
    // ─────────────────────────────────────────
    @Override
    public GatewayInitiateResponse initiate(Payment payment, String webhookUrl) {
        try {
            // STEP 1: get bKash token
            String token = getToken();

            // STEP 2: create payment
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("mode",              "0011");
            requestBody.put("payerReference",    payment.getUserId().toString());
            requestBody.put("callbackURL",       webhookUrl);
            requestBody.put("amount",            payment.getAmount().toString());
            requestBody.put("currency",          payment.getCurrency());
            requestBody.put("intent",            "sale");
            requestBody.put("merchantInvoiceNumber", payment.getId().toString());

            log.info("initiating bKash payment for paymentId: {}", payment.getId());

            Map<?, ?> response = bkashWebClient.post()
                    .uri("/v1.2.0-beta/checkout/payment/create")
                    .header("Authorization", token)
                    .header("X-APP-Key", gatewayProperties.getBkash().getAppKey())
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError(),
                            resp -> resp.bodyToMono(String.class)
                                    .map(body -> new GatewayException("BKASH", 400, body))
                    )
                    .onStatus(
                            status -> status.is5xxServerError(),
                            resp -> resp.bodyToMono(String.class)
                                    .map(body -> new GatewayException("BKASH", 500, body))
                    )
                    .bodyToMono(Map.class)
                    .retryWhen(
                            reactor.util.retry.Retry.backoff(3,
                                    java.time.Duration.ofMillis(500))
                                    .filter(ex -> ex instanceof GatewayException &&
                                            ((GatewayException) ex).getStatusCode() >= 500)
                    )
                    .block();

            if (response == null) {
                throw new GatewayException("BKASH", "null response from gateway");
            }

            String paymentId  = (String) response.get("paymentID");
            String paymentUrl = (String) response.get("bkashURL");

            log.info("bKash payment initiated paymentId: {} bkashPaymentId: {}",
                    payment.getId(), paymentId);

            return GatewayInitiateResponse.builder()
                    .paymentUrl(paymentUrl)
                    .gatewayToken(token)
                    .gatewayRef(paymentId)
                    .build();

        } catch (GatewayException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new GatewayException("BKASH", ex.getMessage());
        }
    }

    // ─────────────────────────────────────────
    // VERIFY WEBHOOK
    // bKash sends paymentID in callback
    // verify by calling bKash execute API
    // ─────────────────────────────────────────
    @Override
    public void verifyWebhook(Map<String, Object> payload, String signature) {
        try {
            String status = (String) payload.get("status");
            if (!"success".equalsIgnoreCase(status)) {
                throw new WebhookVerificationException("BKASH", "payment status is not success");
            }
            log.info("bKash webhook verified status: {}", status);
        } catch (WebhookVerificationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new WebhookVerificationException("BKASH", ex.getMessage());
        }
    }

    // ─────────────────────────────────────────
    // EXTRACT GATEWAY REF
    // ─────────────────────────────────────────
    @Override
    public String extractGatewayRef(Map<String, Object> payload) {
        return (String) payload.get("paymentID");
    }

    // ─────────────────────────────────────────
    // EXTRACT STATUS
    // ─────────────────────────────────────────
    @Override
    public String extractStatus(Map<String, Object> payload) {
        String status = (String) payload.get("status");
        return "success".equalsIgnoreCase(status) ? "SUCCESS" : "FAILED";
    }

    // ─────────────────────────────────────────
    // GET BKASH TOKEN
    // ─────────────────────────────────────────
    private String getToken() {
        Map<String, Object> tokenRequest = new HashMap<>();
        tokenRequest.put("app_key",    gatewayProperties.getBkash().getAppKey());
        tokenRequest.put("app_secret", gatewayProperties.getBkash().getAppSecret());

        Map<?, ?> response = bkashWebClient.post()
                .uri("/v1.2.0-beta/checkout/token/grant")
                .bodyValue(tokenRequest)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null || response.get("id_token") == null) {
            throw new GatewayException("BKASH", "failed to get token");
        }

        return (String) response.get("id_token");
    }
}