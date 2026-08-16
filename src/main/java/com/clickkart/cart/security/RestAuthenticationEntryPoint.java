// clickkart-cart-service/src/main/java/com/clickkart/cart/security/RestAuthenticationEntryPoint.java
package com.clickkart.cart.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Safety net only - {@link com.clickkart.cart.jwt.JwtAuthenticationFilter} already resolves every
 * rejection case itself (missing/malformed/invalid/revoked token, missing correlationId) before a
 * request would reach here. Delegates to the same {@code HandlerExceptionResolver}/{@code
 * GlobalExceptionHandler} pipeline every other error goes through, rather than writing its own
 * response body, so the envelope shape never drifts.
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final HandlerExceptionResolver handlerExceptionResolver;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) {
        handlerExceptionResolver.resolveException(
                request, response, null, new BadCredentialsException("Authentication required", authException));
    }
}
