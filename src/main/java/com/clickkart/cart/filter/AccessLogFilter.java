// clickkart-cart-service/src/main/java/com/clickkart/cart/filter/AccessLogFilter.java
package com.clickkart.cart.filter;

import com.clickkart.cart.constant.LoggerNames;
import com.clickkart.cart.constant.MdcKeys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j(topic = LoggerNames.ACCESS)
public class AccessLogFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        log.info(
                "REQUEST_START method={} uri={} remoteAddr={} correlationId={}",
                request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), MDC.get(MdcKeys.CORRELATION_ID));

        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - start;
            log.info(
                    "REQUEST_END method={} uri={} status={} durationMs={} remoteAddr={} correlationId={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs,
                    request.getRemoteAddr(), MDC.get(MdcKeys.CORRELATION_ID));
        }
    }
}
