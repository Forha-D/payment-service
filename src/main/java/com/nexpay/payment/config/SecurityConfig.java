package com.nexpay.payment.config;

import com.nexpay.payment.middleware.KongAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public KongAuthFilter kongAuthFilter() {
        return new KongAuthFilter();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // disable CSRF — stateless API
            .csrf(AbstractHttpConfigurer::disable)

            // stateless — no session
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            .authorizeHttpRequests(auth -> auth
                // actuator — public
                .requestMatchers("/actuator/**").permitAll()
                // health — public
                .requestMatchers("/health/**").permitAll()
                // webhooks — public but verified by signature
                .requestMatchers("/payment/webhook/**").permitAll()
                // all other payment routes — require authentication
                .anyRequest().authenticated()
            )

            .addFilterBefore(
                kongAuthFilter(),
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }
}