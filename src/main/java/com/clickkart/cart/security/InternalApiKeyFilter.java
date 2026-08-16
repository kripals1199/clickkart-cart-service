// clickkart-cart-service/src/main/java/com/clickkart/cart/security/InternalApiKeyFilter.java
package com.clickkart.cart.security;

import com.clickkart.cart.constant.LoggerNames;
import com.clickkart.cart.constant.MdcKeys;
import com.clickkart.cart.exception.MissingCorrelationIdException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Authenticates the {@code /internal/**} surface with a shared secret.
 *
 * <p>These endpoints resolve <em>any</em> user's address by id, which is precisely the capability
 * the customer-facing API refuses to expose. They exist because Order Service must snapshot a
 * shipping address during checkout, where there is no customer JWT to act on - a retry, a queued
 * step, or a reconciliation job has no token at all.
 *
 * <p><strong>Network placement is not the control.</strong> The endpoints have no Gateway route and
 * the Service is ClusterIP, but that is routing, not authorization - the same argument this service
 * makes for ignoring the Gateway's {@code X-User-Id} header applies here. Anything already inside
 * the cluster can reach this port, so the secret is what actually gates it.
 *
 * <p>Comparison is constant-time. A naive {@code equals} leaks the shared secret one byte at a time
 * to a caller who can measure response latency across many attempts, which is a realistic attack
 * against a long-lived credential that never rotates on its own.
 *
 * <p>A correlation id is still required (Rule 13): internal callers propagate the one Auth Service
 * minted, so a checkout remains traceable across every service that touched it.
 */
@Slf4j(topic = LoggerNames.SECURITY)
public class InternalApiKeyFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-Internal-Api-Key";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    /** Callers hold this authority, which is what {@code SecurityConfig} authorizes the internal paths against. */
    public static final String INTERNAL_ROLE = "ROLE_INTERNAL";

    private final byte[] expectedApiKey;
    private final HandlerExceptionResolver handlerExceptionResolver;
    private final List<String> internalPaths;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public InternalApiKeyFilter(
            String expectedApiKey, HandlerExceptionResolver handlerExceptionResolver, List<String> internalPaths) {
        this.expectedApiKey = expectedApiKey == null ? new byte[0] : expectedApiKey.getBytes(StandardCharsets.UTF_8);
        this.handlerExceptionResolver = handlerExceptionResolver;
        this.internalPaths = internalPaths;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (!isInternalPath(request.getServletPath())) {
            chain.doFilter(request, response);
            return;
        }

        String presented = request.getHeader(API_KEY_HEADER);
        if (presented == null || !matchesExpectedKey(presented)) {
            log.warn("INTERNAL_API_KEY_REJECTED path={} remoteAddr={} keyPresented={}",
                    request.getRequestURI(), request.getRemoteAddr(), presented != null);
            handlerExceptionResolver.resolveException(
                    request, response, null, new BadCredentialsException("Invalid internal API key"));
            return;
        }

        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            handlerExceptionResolver.resolveException(
                    request, response, null,
                    new MissingCorrelationIdException("Request is missing the required X-Correlation-Id header"));
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(
                "internal-service", null, List.of(new SimpleGrantedAuthority(INTERNAL_ROLE)));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        MDC.put(MdcKeys.CORRELATION_ID, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MdcKeys.CORRELATION_ID);
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * An unconfigured key must never authenticate anyone. Without this guard an empty expected key
     * would match an empty presented one and open the whole internal surface to any caller.
     */
    private boolean matchesExpectedKey(String presented) {
        if (expectedApiKey.length == 0) {
            log.error("INTERNAL_API_KEY_NOT_CONFIGURED - refusing every internal request");
            return false;
        }
        return MessageDigest.isEqual(expectedApiKey, presented.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isInternalPath(String path) {
        for (String pattern : internalPaths) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }
}
