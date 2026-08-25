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
import org.springframework.web.reactive.function.client.WebClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
//import java.util.HexFormat;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NagadGatewayService implements PaymentGatewayService {

    @Qualifier("nagadWebClient")
    private final WebClient           nagadWebClient;
    private final GatewayProperties   gatewayProperties;
    private final ObjectMapper        objectMapper;

    // ─────────────────────────────────────────
    // INITIATE PAYMENT
    // ─────────────────────────────────────────
    @Override
    public GatewayInitiateResponse initiate(Payment payment, String webhookUrl) {
        try {
            String merchantId = gatewayProperties.getNagad().getMerchantId();
            String secretKey  = gatewayProperties.getNagad().getSecretKey();

            // build Nagad payment request
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("merchantId",      merchantId);
            requestBody.put("orderId",         payment.getId().toString());
            requestBody.put("amount",          payment.getAmount().toString());
            requestBody.put("currency",        payment.getCurrency());
            requestBody.put("callbackUrl",     webhookUrl);
            requestBody.put("merchantCallbackURL", webhookUrl);

            // sign the request
            String signature = generateHmacSignature(
                    objectMapper.writeValueAsString(requestBody),
                    secretKey
            );
            requestBody.put("signature", signature);

            log.info("initiating Nagad payment for paymentId: {}", payment.getId());

            // call Nagad API
            Map<?, ?> response = nagadWebClient.post()
                    .uri("/api/dfs/check-out/initialize/" + merchantId + "/" + payment.getId())
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError(),
                            resp -> resp.bodyToMono(String.class)
                                    .map(body -> new GatewayException("NAGAD", 400, body))
                    )
                    .onStatus(
                            status -> status.is5xxServerError(),
                            resp -> resp.bodyToMono(String.class)
                                    .map(body -> new GatewayException("NAGAD", 500, body))
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
                throw new GatewayException("NAGAD", "null response from gateway");
            }

            String paymentUrl   = (String) response.get("callBackUrl");
            String gatewayToken = (String) response.get("tokenizedAmount");

            log.info("Nagad payment initiated paymentId: {} paymentUrl: {}",
                    payment.getId(), paymentUrl);

            return GatewayInitiateResponse.builder()
                    .paymentUrl(paymentUrl)
                    .gatewayToken(gatewayToken)
                    .gatewayRef(payment.getId().toString())
                    .build();

        } catch (GatewayException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new GatewayException("NAGAD", ex.getMessage());
        }
    }

    // ─────────────────────────────────────────
    // VERIFY WEBHOOK
    // ─────────────────────────────────────────
    @Override
    public void verifyWebhook(Map<String, Object> payload, String signature) {
        try {
            String secretKey = gatewayProperties.getNagad().getSecretKey();
            String expected  = generateHmacSignature(
                    objectMapper.writeValueAsString(payload),
                    secretKey
            );

            if (!expected.equals(signature)) {
                log.warn("Nagad webhook signature mismatch expected: {} received: {}",
                        expected, signature);
                throw new WebhookVerificationException("NAGAD", "signature mismatch");
            }

            log.info("Nagad webhook signature verified");

        } catch (WebhookVerificationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new WebhookVerificationException("NAGAD", ex.getMessage());
        }
    }

    // ─────────────────────────────────────────
    // EXTRACT GATEWAY REF
    // ─────────────────────────────────────────
    @Override
    public String extractGatewayRef(Map<String, Object> payload) {
        return (String) payload.get("merchantInvoiceNumber");
    }

    // ─────────────────────────────────────────
    // EXTRACT STATUS
    // ─────────────────────────────────────────
    @Override
    public String extractStatus(Map<String, Object> payload) {
        String status = (String) payload.get("status");
        return "Success".equalsIgnoreCase(status) ? "SUCCESS" : "FAILED";
    }

    // ─────────────────────────────────────────
    // HMAC SIGNATURE
    // ─────────────────────────────────────────
    private String generateHmacSignature(String data, String secretKey) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"
        ));
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }
}