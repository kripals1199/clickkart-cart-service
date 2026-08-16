// clickkart-cart-service/src/main/java/com/clickkart/cart/web/RequestMetadata.java
package com.clickkart.cart.web;

/**
 * HTTP-request-derived context threaded from the controllers through the service layer into the
 * audit trail - bundled into one record instead of a growing list of {@code String} parameters on
 * every service method.
 */
public record RequestMetadata(String ipAddress, String userAgent) {

    /**
     * For writes with no HTTP request behind them - the expiry sweeper. Named rather than passing
     * nulls at the call site, so an audit entry with no client attached reads as a deliberate
     * "nobody asked for this" rather than as a missing value someone forgot to thread through.
     */
    public static RequestMetadata systemInitiated() {
        return new RequestMetadata("system", "scheduled-task");
    }
}
