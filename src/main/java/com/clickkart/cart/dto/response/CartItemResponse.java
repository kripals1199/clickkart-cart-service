// src/main/java/com/clickkart/cart/dto/response/CartItemResponse.java
package com.clickkart.cart.dto.response;

import com.clickkart.cart.entity.CartItemEntity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * One basket line as the customer sees it: their own quantity, plus everything else read live from
 * the catalog at render time.
 *
 * <p>Three fields exist to tell the customer something has changed underneath them, and all three
 * matter more than the price itself:
 *
 * <ul>
 *   <li>{@code purchasable} - the listing was pulled, or the SKU no longer exists. The line stays in
 *       the cart rather than vanishing, because a line that silently disappears leaves someone
 *       wondering what they had picked.
 *   <li>{@code priceChanged} and {@code priceWhenAdded} - it costs more (or less) than when it went
 *       in. Shown rather than absorbed: the customer will be charged the live price at checkout, so
 *       hiding the move would mean the basket total and the bill disagree with no explanation.
 *   <li>{@code availability} - a banded hint from Inventory, never an exact count.
 * </ul>
 *
 * <p>{@code lineTotal} is computed from the <strong>live</strong> price, because that is what
 * checkout will charge.
 */
public record CartItemResponse(
        String sku,
        int quantity,
        String productPublicId,
        String productName,
        String variantName,
        String sellerPublicId,
        BigDecimal unitPrice,
        BigDecimal mrp,
        BigDecimal lineTotal,
        boolean purchasable,
        String unpurchasableReason,
        boolean priceChanged,
        BigDecimal priceWhenAdded,
        String availability,
        Instant addedAt) {

    /** A line the catalog could describe. */
    public static CartItemResponse priced(
            CartItemEntity item,
            String productPublicId,
            String productName,
            String variantName,
            String sellerPublicId,
            BigDecimal unitPrice,
            BigDecimal mrp,
            boolean purchasable,
            String unpurchasableReason,
            String availability) {

        BigDecimal lineTotal = unitPrice == null
                ? null
                : unitPrice.multiply(BigDecimal.valueOf(item.getQuantity())).setScale(2, RoundingMode.HALF_UP);

        // Compared with compareTo, never equals: BigDecimal.equals treats 100.0 and 100.00 as
        // different, so a scale change coming back from the catalog would be reported to every
        // customer as a price rise that never happened.
        boolean changed = unitPrice != null
                && item.getPriceWhenAdded() != null
                && unitPrice.compareTo(item.getPriceWhenAdded()) != 0;

        return new CartItemResponse(
                item.getSku(),
                item.getQuantity(),
                productPublicId,
                productName,
                variantName,
                sellerPublicId,
                unitPrice,
                mrp,
                lineTotal,
                purchasable,
                unpurchasableReason,
                changed,
                item.getPriceWhenAdded(),
                availability,
                item.getAddedAt());
    }

    /**
     * A line the catalog could not be asked about, because Product Service was unreachable.
     *
     * <p>Returned rather than failing the whole cart. The SKU and quantity are this service's own
     * data and are still true; only the enrichment is missing. A customer who can see they have
     * three things in their basket during a catalog blip is better served than one shown an error
     * page - and checkout will refuse properly at Order Service, which treats Product as required.
     */
    public static CartItemResponse unpriced(CartItemEntity item) {
        return new CartItemResponse(
                item.getSku(),
                item.getQuantity(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                "Price is temporarily unavailable",
                false,
                item.getPriceWhenAdded(),
                null,
                item.getAddedAt());
    }
}
