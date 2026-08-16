// clickkart-cart-service/src/main/java/com/clickkart/cart/web/ClientIpResolver.java
package com.clickkart.cart.web;

import com.clickkart.cart.config.CartProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for resolving a caller's IP, which every audit event reports.
 *
 * <p>Only honors {@code X-Forwarded-For} when {@code request.getRemoteAddr()} matches a configured
 * trusted-proxy CIDR ({@code user.trusted-proxy-cidrs}) - otherwise a client reaching this service
 * directly could set that header to anything and write a false IP into the tamper-evident audit
 * trail. An empty list means trust nothing, which is the correct default for a service whose
 * network position is unknown. Own copy of Auth Service's resolver (Rule 4: no shared library).
 */
@Component
@RequiredArgsConstructor
public class ClientIpResolver {

    private static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private final CartProperties cartProperties;

    public String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }
        return Optional.ofNullable(request.getHeader(X_FORWARDED_FOR_HEADER))
                .filter(header -> !header.isBlank())
                .map(header -> header.split(",")[0].trim())
                .orElse(remoteAddr);
    }

    private boolean isTrustedProxy(String remoteAddr) {
        List<String> trustedProxyCidrs = cartProperties.getTrustedProxyCidrs();
        if (trustedProxyCidrs.isEmpty()) {
            return false;
        }
        for (String cidr : trustedProxyCidrs) {
            if (new IpAddressMatcher(cidr).matches(remoteAddr)) {
                return true;
            }
        }
        return false;
    }
}
