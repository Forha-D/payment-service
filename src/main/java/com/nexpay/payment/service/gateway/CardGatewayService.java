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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardGatewayService implements PaymentGatewayService {

    @Qualifier("cardWebClient")
    private final WebClient         cardWebClient;
    private final GatewayProperties gatewayProperties;
    //private final ObjectMapper      objectMapper;

    // ─────────────────────────────────────────
    // INITIATE PAYMENT
    // SSL Commerz returns a redirect URL
    // ─────────────────────────────────────────
    @Override
    public GatewayInitiateResponse initiate(Payment payment, String webhookUrl) {
        try {
            String merchantId  = gatewayProperties.getCard().getMerchantId();
            String secretKey   = gatewayProperties.getCard().getSecretKey();

            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("store_id",       merchantId);
            formData.add("store_passwd",   secretKey);
            formData.add("total_amount",   payment.getAmount().toString());
            formData.add("currency",       payment.getCurrency());
            formData.add("tran_id",        payment.getId().toString());
            formData.add("success_url",    webhookUrl + "?status=success");
            formData.add("fail_url",       webhookUrl + "?status=fail");
            formData.add("cancel_url",     webhookUrl + "?status=cancel");
            formData.add("cus_name",       "EKTA User");
            formData.add("cus_email",      payment.getUserId().toString() + "@ekta.app");
            formData.add("cus_phone",      "N/A");
            formData.add("cus_add1",       "Dhaka, Bangladesh");
            formData.add("cus_city",       "Dhaka");
            formData.add("cus_country",    "Bangladesh");
            formData.add("shipping_method","NO");
            formData.add("product_name",   "Wallet Top-up");
            formData.add("product_category","Digital");
            formData.add("product_profile","general");

            log.info("initiating SSL Commerz payment for paymentId: {}", payment.getId());

            Map<?, ?> response = cardWebClient.post()
                    .uri("/gwprocess/v4/api.php")
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError(),
                            resp -> resp.bodyToMono(String.class)
                                    .map(body -> new GatewayException("CARD", 400, body))
                    )
                    .onStatus(
                            status -> status.is5xxServerError(),
                            resp -> resp.bodyToMono(String.class)
                                    .map(body -> new GatewayException("CARD", 500, body))
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
                throw new GatewayException("CARD", "null response from gateway");
            }

            String status     = (String) response.get("status");
            String paymentUrl = (String) response.get("GatewayPageURL");

            if (!"SUCCESS".equalsIgnoreCase(status)) {
                throw new GatewayException("CARD", "initiation failed: " + status);
            }

            log.info("SSL Commerz payment initiated paymentId: {}", payment.getId());

            return GatewayInitiateResponse.builder()
                    .paymentUrl(paymentUrl)
                    .gatewayToken(null)
                    .gatewayRef(payment.getId().toString())
                    .build();

        } catch (GatewayException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new GatewayException("CARD", ex.getMessage());
        }
    }

    // ─────────────────────────────────────────
    // VERIFY WEBHOOK
    // SSL Commerz uses MD5 hash verification
    // ─────────────────────────────────────────
    @Override
    public void verifyWebhook(Map<String, Object> payload, String signature) {
        try {
            String secretKey      = gatewayProperties.getCard().getSecretKey();
            String receivedHash   = (String) payload.get("verify_sign");
            String verifyKey      = (String) payload.get("verify_key");

            if (receivedHash == null || verifyKey == null) {
                throw new WebhookVerificationException("CARD", "missing verification fields");
            }

            // build verification string
            StringBuilder sb = new StringBuilder();
            sb.append(md5(secretKey)).append("&");
            for (String key : verifyKey.split(",")) {
                Object value = payload.get(key.trim());
                sb.append(key.trim()).append("=")
                  .append(value != null ? value.toString() : "")
                  .append("&");
            }

            String expected = md5(sb.toString().replaceAll("&$", ""));

            if (!expected.equals(receivedHash)) {
                throw new WebhookVerificationException("CARD", "hash mismatch");
            }

            log.info("SSL Commerz webhook verified");

        } catch (WebhookVerificationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new WebhookVerificationException("CARD", ex.getMessage());
        }
    }

    // ─────────────────────────────────────────
    // EXTRACT GATEWAY REF
    // ─────────────────────────────────────────
    @Override
    public String extractGatewayRef(Map<String, Object> payload) {
        return (String) payload.get("bank_tran_id");
    }

    // ─────────────────────────────────────────
    // EXTRACT STATUS
    // ─────────────────────────────────────────
    @Override
    public String extractStatus(Map<String, Object> payload) {
        String status = (String) payload.get("status");
        return "VALID".equalsIgnoreCase(status) ? "SUCCESS" : "FAILED";
    }

    // ─────────────────────────────────────────
    // MD5 HASH
    // ─────────────────────────────────────────
    private String md5(String input) throws Exception {
        MessageDigest md    = MessageDigest.getInstance("MD5");
        byte[]        hash  = md.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb    = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}