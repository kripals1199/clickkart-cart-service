// src/main/java/com/clickkart/cart/entity/CartItemEntity.java
package com.clickkart.cart.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One line of a basket: a SKU and how many.
 *
 * <p><strong>Deliberately absent: the product name, the variant name, the seller, and the current
 * price.</strong> All four are read live from Product Service when the cart is rendered, and none is
 * stored here. That is the opposite of what {@code OrderItemEntity} does one service along, and the
 * difference is the whole point of the two tables:
 *
 * <ul>
 *   <li>An <strong>order</strong> is a record of an agreement at a point in time. It must keep its
 *       own copy of the terms, or last year's orders decay into blanks as sellers tidy their
 *       catalogs and repricing rewrites what a customer already paid.
 *   <li>A <strong>cart</strong> is a list of intentions. Nothing has been agreed, so a stored name or
 *       price is not a record of anything - it is just a copy that goes stale, and a customer shown
 *       last week's price for something they have not bought yet has simply been misinformed.
 * </ul>
 *
 * <p>The one price that <em>is</em> stored is {@link #priceWhenAdded}, and it is not what the
 * customer is charged - see its own note.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "cart_items",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_cart_items_cart_sku",
                        columnNames = {"cart_id", "sku"}),
        indexes = @Index(name = "ix_cart_items_cart", columnList = "cart_id"))
public class CartItemEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private CartEntity cart;

    @Column(name = "sku", nullable = false, updatable = false, length = 60)
    private String sku;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    /**
     * What this cost when it went into the basket. <strong>A change detector, not a charging basis.</strong>
     *
     * <p>Product Service's own API suggests callers snapshot the price at add-to-cart time so it
     * cannot move underneath them. That advice is aimed at a caller that charges from its copy; this
     * service never charges, and Order Service re-prices against the catalog at checkout because that
     * is the moment terms are actually agreed. If Cart froze a price and Order charged a different
     * one, the customer would be shown one number and billed another - which is the exact harm the
     * advice exists to prevent, arriving through the back door.
     *
     * <p>So the cart renders the <em>live</em> price and uses this only to say "this went up since
     * you added it". Making the change visible is worth far more than pretending it did not happen.
     */
    @Column(name = "price_when_added", precision = 12, scale = 2)
    private BigDecimal priceWhenAdded;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    public static CartItemEntity of(String sku, int quantity, BigDecimal priceWhenAdded) {
        CartItemEntity item = new CartItemEntity();
        item.setSku(sku);
        item.setQuantity(quantity);
        item.setPriceWhenAdded(priceWhenAdded);
        item.setAddedAt(Instant.now());
        return item;
    }
}
