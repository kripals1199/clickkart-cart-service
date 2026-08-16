// src/main/java/com/clickkart/cart/dto/response/CartResponse.java
package com.clickkart.cart.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The whole basket.
 *
 * <p>{@code subtotal} counts only purchasable lines. Including a delisted item in the total would
 * quote the customer a figure that checkout cannot honour - and the number a basket shows is the one
 * people decide by, so it has to be the number they will actually be asked for.
 *
 * <p>There is no shipping and no grand total here, deliberately. Shipping depends on the delivery
 * address, which Cart does not know and has no business asking for; Order Service works it out at
 * checkout. A cart that guessed would be wrong exactly when it mattered - at the point the customer
 * decides whether the total is worth it.
 *
 * <p>{@code readyForCheckout} is the single field a client should branch on before offering the
 * button. It is false for an empty cart and for one holding anything the catalog will not sell.
 */
public record CartResponse(
        List<CartItemResponse> items,
        int distinctItems,
        int totalQuantity,
        BigDecimal subtotal,
        boolean readyForCheckout,
        boolean pricingDegraded,
        Instant lastActivityAt) {

    public static CartResponse of(List<CartItemResponse> items, Instant lastActivityAt) {
        BigDecimal subtotal = items.stream()
                .filter(CartItemResponse::purchasable)
                .map(CartItemResponse::lineTotal)
                .filter(total -> total != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, java.math.RoundingMode.HALF_UP);

        int totalQuantity = items.stream().mapToInt(CartItemResponse::quantity).sum();

        // "Could not price it" is not the same as "will not sell it", so it gets its own flag. A
        // client that treated a catalog outage as a delisted product would tell the customer their
        // item is gone when it is merely unreachable.
        boolean degraded = items.stream().anyMatch(item -> item.unitPrice() == null);

        boolean ready = !items.isEmpty() && items.stream().allMatch(CartItemResponse::purchasable);

        return new CartResponse(items, items.size(), totalQuantity, subtotal, ready, degraded, lastActivityAt);
    }

    public static CartResponse empty(Instant lastActivityAt) {
        return new CartResponse(List.of(), 0, 0, BigDecimal.ZERO.setScale(2), false, false, lastActivityAt);
    }
}
