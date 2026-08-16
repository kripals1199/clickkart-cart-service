// src/main/java/com/clickkart/cart/exception/SkuNotAddableException.java
package com.clickkart.cart.exception;

import lombok.Getter;

/**
 * The catalog will not sell this SKU, so it is refused at the moment of adding.
 *
 * <p>Checked on the way in rather than only at checkout, because a basket that silently accepts
 * things nobody can buy wastes the customer's time twice - once filling it and again being told at
 * the till. A listing pulled <em>after</em> it was added is a different case and is handled
 * differently: that line stays in the cart, flagged, because it was legitimately added and the
 * customer should see what happened to it.
 *
 * <p>Carries Product Service's own wording rather than restating it. That service decides how much
 * to disclose about why a listing is not live - deliberately little, so a competitor cannot learn
 * from a failed add that a rival's product is in review - and a second phrasing here would
 * eventually contradict it.
 */
@Getter
public class SkuNotAddableException extends RuntimeException {

    private final String sku;

    public SkuNotAddableException(String sku, String reason) {
        super(reason);
        this.sku = sku;
    }
}
