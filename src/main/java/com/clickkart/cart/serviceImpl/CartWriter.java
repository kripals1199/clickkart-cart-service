// src/main/java/com/clickkart/cart/serviceImpl/CartWriter.java
package com.clickkart.cart.serviceImpl;

import com.clickkart.cart.entity.CartEntity;
import com.clickkart.cart.entity.CartItemEntity;
import com.clickkart.cart.exception.CartTooLargeException;
import com.clickkart.cart.repository.CartRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every database write this service makes, each one its own short transaction.
 *
 * <p>Same shape and the same two reasons as Order Service's {@code OrderWriter}, which is where the
 * pattern is explained at length:
 *
 * <ul>
 *   <li><strong>Remote calls stay outside transactions.</strong> Adding to a cart asks Product
 *       Service whether the SKU is sellable; holding a database connection across that call would
 *       pin one for as long as the catalog takes to answer.
 *   <li><strong>It is a separate bean because Spring's transactions are proxy-based.</strong>
 *       {@link Propagation#REQUIRES_NEW} on a method called from within the same class is silently
 *       ignored, and the create-race recovery in {@code CartServiceImpl} depends on this committing
 *       independently.
 * </ul>
 *
 * <p>Every method re-reads by {@code userPublicId} rather than accepting a passed-in entity, so
 * nothing here can mutate a detached object and lose the change at commit - the failure that cost
 * Inventory Service a working reservation lifecycle and took a live database to notice.
 */
@Component
@RequiredArgsConstructor
public class CartWriter {

    private final CartRepository cartRepository;

    /**
     * Adds a line, or increases the one already there.
     *
     * <p>{@link Propagation#REQUIRES_NEW} so that a losing race on the unique constraint over
     * {@code user_public_id} does not poison the caller's transaction - the caller retries, and a
     * shared transaction marked rollback-only would make that retry fail for a second, unrelated
     * reason.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CartEntity addOrIncrement(
            String userPublicId,
            String sku,
            int quantity,
            BigDecimal priceNow,
            int maxDistinctItems,
            int maxQuantityPerItem) {

        CartEntity cart = cartRepository
                .findByUserPublicId(userPublicId)
                .orElseGet(() -> CartEntity.forUser(userPublicId));

        Optional<CartItemEntity> existing = cart.findItem(sku);
        if (existing.isPresent()) {
            CartItemEntity item = existing.get();
            int combined = item.getQuantity() + quantity;
            if (combined > maxQuantityPerItem) {
                throw new CartTooLargeException("You may have at most " + maxQuantityPerItem + " of any one item");
            }
            item.setQuantity(combined);
            // priceWhenAdded is deliberately NOT refreshed. It marks when the customer decided to
            // buy this, and refreshing it on every increment would erase the very change the cart
            // exists to point out - quietly, and precisely when the price had just moved.
        } else {
            if (cart.getItems().size() >= maxDistinctItems) {
                throw new CartTooLargeException(
                        "A cart may hold at most " + maxDistinctItems + " different items");
            }
            cart.addItem(CartItemEntity.of(sku, quantity, priceNow));
        }

        cart.touch();
        return cartRepository.saveAndFlush(cart);
    }

    /** Sets a line to an absolute quantity. Zero removes it; removing the last line leaves an empty cart. */
    @Transactional
    public CartEntity setQuantity(String userPublicId, String sku, int quantity) {
        CartEntity cart = cartRepository
                .findByUserPublicId(userPublicId)
                .orElseGet(() -> CartEntity.forUser(userPublicId));

        cart.findItem(sku).ifPresent(item -> {
            if (quantity <= 0) {
                cart.removeItem(item);
            } else {
                item.setQuantity(quantity);
            }
        });

        cart.touch();
        return cartRepository.saveAndFlush(cart);
    }

    /**
     * Empties a cart by deleting the row outright, rather than leaving an empty one behind.
     *
     * <p>An empty cart row is indistinguishable from a cart nobody has, so keeping it would mean
     * carrying a row per customer who ever emptied one - and the pruning job would then be the only
     * thing that ever cleaned them up, ninety days late.
     */
    @Transactional
    public void clear(String userPublicId) {
        cartRepository.findByUserPublicId(userPublicId).ifPresent(cartRepository::delete);
    }

    /**
     * Deletes an abandoned cart, re-checking the deadline inside the transaction.
     *
     * <p>The re-check is what makes the pruning job safe against a customer who returns to a cart
     * between it being listed and being deleted. Without it, a sweep that started an hour ago could
     * throw away a basket somebody is filling right now.
     */
    @Transactional
    public boolean deleteIfStillAbandoned(String userPublicId, Instant cutoff) {
        return cartRepository
                .findByUserPublicId(userPublicId)
                .filter(cart -> cart.getLastActivityAt().isBefore(cutoff))
                .map(cart -> {
                    cartRepository.delete(cart);
                    return true;
                })
                .orElse(false);
    }
}
