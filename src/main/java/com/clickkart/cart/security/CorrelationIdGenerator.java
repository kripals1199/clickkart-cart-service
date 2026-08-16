// clickkart-cart-service/src/main/java/com/clickkart/cart/security/CorrelationIdGenerator.java
package com.clickkart.cart.security;

import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Two callers, both of them outside the reach of Rule 13's "only Auth Service mints" rule because
 * neither is on an authenticated request path.
 *
 * <p>{@code CorrelationIdFilter} uses it for requests that have not been authenticated yet, so the
 * access log can name a request that is about to be turned away - see {@code WebConfig}.
 *
 * <p>{@code OrderServiceImpl.sweepExpiredOrders} uses it because a scheduled sweep has no inbound
 * request to take an id from, and it still has to call Inventory to hand stock back. Sending no
 * correlation id there is not an option: Inventory's internal API rejects a request without one, so
 * the choice is between minting an id and the sweeper being unable to release anything.
 */
@Component
public class CorrelationIdGenerator {

    public String generate() {
        return UUID.randomUUID().toString();
    }
}
