// clickkart-cart-service/src/main/java/com/clickkart/cart/security/RestAccessDeniedHandler.java
package com.clickkart.cart.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Defense in depth for {@code AccessDeniedException} thrown directly inside the security filter
 * chain (rather than from {@code @PreAuthorize}, which Spring MVC's dispatch already routes to
 * {@code GlobalExceptionHandler} without this handler's help). Delegates to the same
 * resolver/handler pipeline either way, so a 403 looks identical regardless of which layer caught it.
 */
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final HandlerExceptionResolver handlerExceptionResolver;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) {
        handlerExceptionResolver.resolveException(request, response, null, accessDeniedException);
    }
}
