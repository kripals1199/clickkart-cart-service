// clickkart-cart-service/src/main/java/com/clickkart/cart/filter/CorrelationIdFilter.java
package com.clickkart.cart.filter;

import com.clickkart.cart.constant.MdcKeys;
import com.clickkart.cart.security.CorrelationIdGenerator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Unlike Notification/Audit Log Service's identically-named filter - pure *receivers* that
 * reject a missing {@code X-Correlation-Id} outright, since their sole caller (Auth Service's
 * Feign clients) always sets it - this service's {@code POST /challenge} is reached directly by
 * an anonymous browser through the Gateway, which passes public paths through untouched (see
 * {@code JwtAuthenticationGlobalFilter}'s Javadoc). There is no upstream minter to require a
 * value from here, so this filter *mints* a correlation id when one is missing/blank instead of
 * rejecting the request - the same posture Auth Service itself takes as the platform's original
 * minter (Rule 13). An explicitly supplied header (e.g. from Auth Service's own Feign call to
 * {@code /verify}) is still honored and propagated as-is.
 *
 * <p>Health/Swagger/actuator paths are exempt - k8s probes and API-doc scrapers have no
 * correlation id to send and none is meaningful for them.
 */
@RequiredArgsConstructor
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    private final CorrelationIdGenerator correlationIdGenerator;
    private final List<String> exemptPaths;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isExempt(request.getServletPath())) {
            chain.doFilter(request, response);
            return;
        }

        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = correlationIdGenerator.generate();
        }

        MDC.put(MdcKeys.CORRELATION_ID, correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MdcKeys.CORRELATION_ID);
        }
    }

    private boolean isExempt(String path) {
        for (String pattern : exemptPaths) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }
}
