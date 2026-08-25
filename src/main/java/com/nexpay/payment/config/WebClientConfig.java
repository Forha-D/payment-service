package com.nexpay.payment.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
@RequiredArgsConstructor
public class WebClientConfig {

    private final GatewayProperties gatewayProperties;

    // ─────────────────────────────────────────
    // NAGAD WEB CLIENT
    // ─────────────────────────────────────────
    @Bean("nagadWebClient")
    public WebClient nagadWebClient() {
        return WebClient.builder()
                .baseUrl(gatewayProperties.getNagad().getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient()))
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("X-KM-Api-Version", "v-0.2.0")
                .build();
    }

    // ─────────────────────────────────────────
    // BKASH WEB CLIENT
    // ─────────────────────────────────────────
    @Bean("bkashWebClient")
    public WebClient bkashWebClient() {
        return WebClient.builder()
                .baseUrl(gatewayProperties.getBkash().getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient()))
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    // ─────────────────────────────────────────
    // STRIPE WEB CLIENT
    // ─────────────────────────────────────────
    @Bean("stripeWebClient")
    public WebClient stripeWebClient() {
        return WebClient.builder()
                .baseUrl("https://api.stripe.com")
                .clientConnector(new ReactorClientHttpConnector(httpClient()))
                .defaultHeader("Content-Type", "application/x-www-form-urlencoded")
                .defaultHeader("Authorization",
                        "Bearer " + gatewayProperties.getStripe().getSecretKey())
                .build();
    }

    // ─────────────────────────────────────────
    // CARD (SSL Commerz) WEB CLIENT
    // ─────────────────────────────────────────
    @Bean("cardWebClient")
    public WebClient cardWebClient() {
        return WebClient.builder()
                .baseUrl(gatewayProperties.getCard().getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient()))
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    // ─────────────────────────────────────────
    // SHARED HTTP CLIENT — timeout config
    // ─────────────────────────────────────────
    private HttpClient httpClient() {
        return HttpClient.create()
                // connection timeout — 5s to establish TCP connection
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                // response timeout — 30s to wait for gateway response
                // gateways can be slow, 30s is reasonable
                .responseTimeout(Duration.ofSeconds(30))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(30,  TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(10, TimeUnit.SECONDS))
                );
    }
}