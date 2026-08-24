package com.nexpay.payment.middleware;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Slf4j
public class KongAuthFilter extends OncePerRequestFilter {

    private static final String USER_ID_HEADER = "X-User-ID";
    private static final String ROLE_HEADER    = "X-Role";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain         filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();

        // allow actuator health checks — no auth needed
        if (path.startsWith("/actuator")) {
            filterChain.doFilter(request, response);
            return;
        }

        // allow health endpoints — no auth needed
        if (path.startsWith("/health")) {
            filterChain.doFilter(request, response);
            return;
        }

        // allow webhook endpoints — no JWT, gateway calls these
        // security handled by signature verification per gateway
        if (path.startsWith("/payment/webhook")) {
            filterChain.doFilter(request, response);
            return;
        }

        // extract Kong injected headers
        String userId = request.getHeader(USER_ID_HEADER);
        String role   = request.getHeader(ROLE_HEADER);

        // block if Kong headers are missing
        if (userId == null || userId.isBlank()) {
            log.warn("missing X-User-ID header on path: {}", path);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"message\":\"unauthorized\"}");
            return;
        }

        // build authorities from role header
        List<SimpleGrantedAuthority> authorities = (role != null && !role.isBlank())
                ? Arrays.stream(role.split(","))
                        .map(String::trim)
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                        .toList()
                : Collections.emptyList();

        // set authentication in security context
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        authorities
                );

        SecurityContextHolder.getContext().setAuthentication(auth);

        log.debug("authenticated user: {} role: {} path: {}", userId, role, path);

        filterChain.doFilter(request, response);
    }
}