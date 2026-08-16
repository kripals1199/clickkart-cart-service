// src/main/java/com/clickkart/cart/feign/AvailabilityApiResponse.java
package com.clickkart.cart.feign;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Inventory's banded verdict, unwrapped from the standard envelope.
 *
 * <p>{@code quantity} is deliberately not bound even though Inventory populates it inside the LOW
 * band. Binding it would put an exact count in this service's memory and, sooner or later, in a
 * response - and the banding exists to keep that number away from anyone who can call a public API.
 * The band alone is what a cart needs to say "low stock".
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AvailabilityApiResponse(boolean success, Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(String sku, boolean inStock, String band) {}

    public String bandOrNull() {
        return data == null ? null : data.band();
    }
}
