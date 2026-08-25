package com.nexpay.payment.service.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StripeGatewayService implements PaymentGatewayService {

    @Qualifier("stripeWebClient")
    private final WebClient         stripeWebClient;
    private final GatewayProperties gatewayProperties;
    private final ObjectMapper      objectMapper;

    // ─────────────────────────────────────────
    // INITIATE PAYMENT
    // Stripe uses PaymentIntent
    // ─────────────────────────────────────────
    @Override
    public GatewayInitiateResponse initiate(Payment payment, String webhookUrl) {
        try {
            // Stripe amount is in smallest currency unit (paisa for BDT)
            long amountInPaisa = payment.getAmount()
                    .multiply(new java.math.BigDecimal("100"))
                    .longValue();

            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("amount",   String.valueOf(amountInPaisa));
            formData.add("currency", payment.getCurrency().toLowerCase());
            formData.add("metadata[payment_id]", payment.getId().toString());
            formData.add("metadata[user_id]",    payment.getUserId().toString());

            log.info("initiating Stripe payment for paymentId: {}", payment.getId());

            Map<?, ?> response = stripeWebClient.post()
                    .uri("/v1/payment_intents")
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError(),
                            resp -> resp.bodyToMono(String.class)
                                    .map(body -> new GatewayException("STRIPE", 400, body))
                    )
                    .onStatus(
                            status -> status.is5xxServerError(),
                            resp -> resp.bodyToMono(String.class)
                                    .map(body -> new GatewayException("STRIPE", 500, body))
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
                throw new GatewayException("STRIPE", "null response from gateway");
            }

            String paymentIntentId = (String) response.get("id");
            String clientSecret    = (String) response.get("client_secret");

            log.info("Stripe payment intent created paymentId: {} intentId: {}",
                    payment.getId(), paymentIntentId);

            return GatewayInitiateResponse.builder()
                    .paymentUrl(clientSecret) // Flutter uses client_secret to show Stripe UI
                    .gatewayToken(clientSecret)
                    .gatewayRef(paymentIntentId)
                    .build();

        } catch (GatewayException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new GatewayException("STRIPE", ex.getMessage());
        }
    }

    // ─────────────────────────────────────────
    // VERIFY WEBHOOK
    // Stripe uses Stripe-Signature header
    // ─────────────────────────────────────────
    @Override
    public void verifyWebhook(Map<String, Object> payload, String signature) {
        try {
            if (signature == null || signature.isBlank()) {
                throw new WebhookVerificationException("STRIPE", "missing signature header");
            }

            String webhookSecret = gatewayProperties.getStripe().getWebhookSecret();
            String payloadString = objectMapper.writeValueAsString(payload);

            // extract timestamp from signature header
            // format: t=timestamp,v1=signature
            String timestamp = null;
            String sigHash   = null;
            for (String part : signature.split(",")) {
                if (part.startsWith("t=")) timestamp = part.substring(2);
                if (part.startsWith("v1=")) sigHash  = part.substring(3);
            }

            if (timestamp == null || sigHash == null) {
                throw new WebhookVerificationException("STRIPE", "invalid signature format");
            }

            // compute expected signature
            String signedPayload = timestamp + "." + payloadString;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"
            ));
            byte[] hash    = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
            String expected = bytesToHex(hash);

            if (!expected.equals(sigHash)) {
                throw new WebhookVerificationException("STRIPE", "signature mismatch");
            }

            log.info("Stripe webhook signature verified");

        } catch (WebhookVerificationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new WebhookVerificationException("STRIPE", ex.getMessage());
        }
    }

    // ─────────────────────────────────────────
    // EXTRACT GATEWAY REF
    // ─────────────────────────────────────────
@Override
public String extractGatewayRef(Map<String, Object> payload) {

    Object dataValue = payload.get("data");

    if (!(dataValue instanceof Map<?, ?> data)) {
        return null;
    }

    Object objectValue = data.get("object");

    if (!(objectValue instanceof Map<?, ?> object)) {
        return null;
    }

    Object idValue = object.get("id");

    return idValue instanceof String id ? id : null;
}
    // ─────────────────────────────────────────
    // EXTRACT STATUS
    // ─────────────────────────────────────────
    @Override
    public String extractStatus(Map<String, Object> payload) {
        String eventType = (String) payload.get("type");
        return "payment_intent.succeeded".equals(eventType) ? "SUCCESS" : "FAILED";
    }

    // ─────────────────────────────────────────
    // BYTES TO HEX
    // ─────────────────────────────────────────
    private String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}