// clickkart-cart-service/src/main/java/com/clickkart/cart/security/RevocationService.java
package com.clickkart.cart.security;

import com.clickkart.cart.config.CartProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Read-only view of the {@code revoked:jti:<jti>} keyspace Auth Service writes on logout and the
 * Gateway also checks. This service only ever reads it - revoking is Auth Service's job, and the
 * least-privilege intent is that a compromise here cannot forge a revocation or un-revoke a token.
 *
 * <p>Without this check, an access token would remain usable against this service until its
 * natural expiry even after the customer logged out, because a JWT carries no signal that it was
 * invalidated after issuance. Signature validation alone cannot express "logged out".
 *
 * <p>Redis errors are deliberately not swallowed here. The caller ({@code JwtAuthenticationFilter})
 * catches {@code DataAccessException} itself so it can log the jti and path, and fails the request
 * with 503 rather than treating an unreachable Redis as "not revoked" - which would silently
 * restore access to every logged-out token for the duration of the outage.
 */
@Service
@RequiredArgsConstructor
public class RevocationService {

    private final StringRedisTemplate redisTemplate;
    private final CartProperties cartProperties;

    public boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank()) {
            // Tokens without a jti cannot be individually revoked - treat as not revoked rather
            // than failing every request that predates jti support. Matches the Gateway.
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(cartProperties.getRevocationKeyPrefix() + jti));
    }
}
