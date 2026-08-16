// src/main/java/com/clickkart/cart/entity/CartEntity.java
package com.clickkart.cart.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One customer's basket. Exactly one row per person, ever.
 *
 * <p><strong>This is the least authoritative table on the platform, and that shapes everything.</strong>
 * A cart holds no money, reserves no stock, and promises nothing. Losing one is an annoyance; losing
 * an order or a payment is an incident. So there is no audit trail here, no hash chain, no
 * reservation, and no attempt at strict consistency - all of which the services either side of it
 * have, and all of which would be ceremony on a list of things somebody might buy.
 *
 * <p><strong>The one thing it must not do is hold stock.</strong> Adding to a cart deliberately
 * reserves nothing. If it did, browsing would take goods off sale, and a shop whose stock is consumed
 * by window-shoppers runs out of things to sell without selling anything. Availability is shown as a
 * hint, and the truth is established at checkout by Inventory Service.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "carts",
        uniqueConstraints =
                @UniqueConstraint(name = "uk_carts_user", columnNames = "user_public_id"),
        indexes = @Index(name = "ix_carts_last_activity", columnList = "last_activity_at"))
public class CartEntity extends BaseEntity {

    /**
     * The owner, and the only key this table is ever looked up by. There is no separate public id
     * because a cart is never referenced from outside: the customer's routes say "my cart" and
     * Order Service names the user, so an id of its own would be one more thing to get wrong.
     */
    @Column(name = "user_public_id", nullable = false, updatable = false, length = 40)
    private String userPublicId;

    /**
     * Touched by every write, and read by the pruning job.
     *
     * <p>Distinct from {@code updatedDate} on the base entity, which JPA maintains and which a
     * schema migration or a backfill would move without the customer having done anything. This one
     * means "the person last did something", which is the only sense in which a cart is abandoned.
     */
    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    /**
     * Eager because a cart is never useful without its lines and every read renders them. Lazy would
     * mean a guaranteed second query on every single read, plus the detachment traps that come with
     * it - the class of bug that cost this codebase a working reservation lifecycle.
     */
    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<CartItemEntity> items = new ArrayList<>();

    public static CartEntity forUser(String userPublicId) {
        CartEntity cart = new CartEntity();
        cart.setUserPublicId(userPublicId);
        cart.setLastActivityAt(Instant.now());
        return cart;
    }

    public Optional<CartItemEntity> findItem(String sku) {
        return items.stream().filter(item -> item.getSku().equals(sku)).findFirst();
    }

    public void addItem(CartItemEntity item) {
        items.add(item);
        item.setCart(this);
    }

    public void removeItem(CartItemEntity item) {
        items.remove(item);
        item.setCart(null);
    }

    public void touch() {
        lastActivityAt = Instant.now();
    }
}
