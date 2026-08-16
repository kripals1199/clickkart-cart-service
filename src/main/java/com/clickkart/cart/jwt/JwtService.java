// clickkart-cart-service/src/main/java/com/clickkart/cart/jwt/JwtService.java
package com.clickkart.cart.jwt;

import com.clickkart.cart.config.CartProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Validates HMAC-SHA256-signed access tokens minted by Auth Service. Validation only - this
 * service never issues a token, so there is no signing method here at all. The signing key is a
 * shared secret (JWT_SECRET) configured independently on Auth Service, the Gateway and here;
 * there is no shared library, so all three must be deployed with the same value.
 */
@Service
public class JwtService {

    private final SecretKey key;

    public JwtService(CartProperties cartProperties) {
        this.key = Keys.hmacShaKeyFor(cartProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @throws JwtException if the token is malformed, expired, or has an invalid signature
     */
    public Claims parseAndValidate(String token) throws JwtException {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
