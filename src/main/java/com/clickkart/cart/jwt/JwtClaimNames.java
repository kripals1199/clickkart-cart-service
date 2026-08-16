// clickkart-cart-service/src/main/java/com/clickkart/cart/jwt/JwtClaimNames.java
package com.clickkart.cart.jwt;

/**
 * Custom (non-registered) claim keys minted into every access token by Auth Service's {@code
 * JwtService}. No shared library exists between services (Rule 4), so this is a deliberate local
 * copy of the same literal values also duplicated in {@code
 * com.clickkart.auth.jwt.JwtClaimNames} and {@code com.clickkart.gateway.filter.JwtClaimNames} -
 * if any copy drifts, claim extraction silently breaks and needs a manual cross-check.
 */
public final class JwtClaimNames {

    private JwtClaimNames() {}

    public static final String ROLES = "roleTypes";
    public static final String CORRELATION_ID = "correlationId";
}
