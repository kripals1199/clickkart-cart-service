// clickkart-cart-service/src/main/java/com/clickkart/cart/security/AuthenticatedPrincipal.java
package com.clickkart.cart.security;

import java.time.Instant;
import java.util.Set;

/**
 * What the service knows about the caller, derived entirely from a signature-verified JWT.
 *
 * <p>{@code userId} is Auth Service's {@code publicId} (the token's {@code sub} claim) and is the
 * only value the service will ever treat as "who is asking". It is never read from a request
 * header - see {@code JwtAuthenticationFilter}.
 */
public record AuthenticatedPrincipal(
        String userId, Set<String> roles, String correlationId, String jti, Instant expiresAt) {}
