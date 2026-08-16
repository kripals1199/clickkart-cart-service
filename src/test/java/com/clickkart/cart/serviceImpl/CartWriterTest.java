// src/test/java/com/clickkart/cart/serviceImpl/CartWriterTest.java
package com.clickkart.cart.serviceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clickkart.cart.entity.CartEntity;
import com.clickkart.cart.entity.CartItemEntity;
import com.clickkart.cart.exception.CartTooLargeException;
import com.clickkart.cart.repository.CartRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** The mutation rules, on real entities. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CartWriterTest {

    private static final String USER = "usr_alice";
    private static final String SKU = "SKU-A";

    @Mock private CartRepository cartRepository;

    private CartWriter writer;

    @BeforeEach
    void setUp() {
        writer = new CartWriter(cartRepository);
        when(cartRepository.saveAndFlush(any(CartEntity.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    @DisplayName("a first add creates the cart and records the price it went in at")
    void firstAddCreatesCart() {
        when(cartRepository.findByUserPublicId(USER)).thenReturn(Optional.empty());

        CartEntity cart = writer.addOrIncrement(USER, SKU, 2, new BigDecimal("100.00"), 50, 100);

        assertThat(cart.getUserPublicId()).isEqualTo(USER);
        assertThat(cart.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getSku()).isEqualTo(SKU);
            assertThat(item.getQuantity()).isEqualTo(2);
            assertThat(item.getPriceWhenAdded()).isEqualByComparingTo("100.00");
        });
    }

    @Test
    @DisplayName("adding the same SKU increases the quantity rather than making a second line")
    void addingAgainIncrements() {
        when(cartRepository.findByUserPublicId(USER)).thenReturn(Optional.of(cartWith(SKU, 2, "100.00")));

        CartEntity cart = writer.addOrIncrement(USER, SKU, 3, new BigDecimal("120.00"), 50, 100);

        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(5);
    }

    /**
     * The price recorded is the one the customer decided at. Refreshing it on every increment would
     * erase the change the cart exists to point out - quietly, and exactly when it had just moved.
     */
    @Test
    @DisplayName("an increment does not overwrite the recorded price")
    void incrementKeepsOriginalPrice() {
        when(cartRepository.findByUserPublicId(USER)).thenReturn(Optional.of(cartWith(SKU, 1, "100.00")));

        CartEntity cart = writer.addOrIncrement(USER, SKU, 1, new BigDecimal("120.00"), 50, 100);

        assertThat(cart.getItems().get(0).getPriceWhenAdded()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("the per-item cap is enforced on the combined quantity, not just the increment")
    void perItemCapCountsTheTotal() {
        when(cartRepository.findByUserPublicId(USER)).thenReturn(Optional.of(cartWith(SKU, 98, "100.00")));

        assertThatThrownBy(() -> writer.addOrIncrement(USER, SKU, 5, new BigDecimal("100.00"), 50, 100))
                .isInstanceOf(CartTooLargeException.class)
                .hasMessageContaining("100");
    }

    @Test
    @DisplayName("the distinct-item cap refuses a new line but never blocks increasing an existing one")
    void distinctCapDoesNotBlockIncrements() {
        CartEntity full = CartEntity.forUser(USER);
        for (int i = 0; i < 3; i++) {
            full.addItem(CartItemEntity.of("SKU-" + i, 1, new BigDecimal("10.00")));
        }
        when(cartRepository.findByUserPublicId(USER)).thenReturn(Optional.of(full));

        assertThatThrownBy(() -> writer.addOrIncrement(USER, "SKU-NEW", 1, new BigDecimal("10.00"), 3, 100))
                .isInstanceOf(CartTooLargeException.class);

        // A cart at its line limit must still let someone buy two of something already in it.
        assertThat(writer.addOrIncrement(USER, "SKU-1", 1, new BigDecimal("10.00"), 3, 100)
                        .findItem("SKU-1")
                        .orElseThrow()
                        .getQuantity())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("setting a quantity to zero removes the line")
    void zeroRemovesTheLine() {
        when(cartRepository.findByUserPublicId(USER)).thenReturn(Optional.of(cartWith(SKU, 4, "100.00")));

        assertThat(writer.setQuantity(USER, SKU, 0).getItems()).isEmpty();
    }

    @Test
    @DisplayName("setting a quantity is absolute, so a retried click is harmless")
    void setQuantityIsAbsolute() {
        when(cartRepository.findByUserPublicId(USER)).thenReturn(Optional.of(cartWith(SKU, 4, "100.00")));

        assertThat(writer.setQuantity(USER, SKU, 7).getItems().get(0).getQuantity()).isEqualTo(7);
        assertThat(writer.setQuantity(USER, SKU, 7).getItems().get(0).getQuantity()).isEqualTo(7);
    }

    @Test
    @DisplayName("every write touches the activity clock, so the pruner does not take a live cart")
    void writesTouchTheClock() {
        CartEntity old = cartWith(SKU, 1, "100.00");
        old.setLastActivityAt(Instant.now().minus(200, ChronoUnit.DAYS));
        when(cartRepository.findByUserPublicId(USER)).thenReturn(Optional.of(old));

        CartEntity after = writer.addOrIncrement(USER, SKU, 1, new BigDecimal("100.00"), 50, 100);

        assertThat(after.getLastActivityAt()).isAfter(Instant.now().minus(1, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("clearing deletes the row rather than leaving an empty cart behind")
    void clearDeletesTheRow() {
        CartEntity cart = cartWith(SKU, 1, "100.00");
        when(cartRepository.findByUserPublicId(USER)).thenReturn(Optional.of(cart));

        writer.clear(USER);

        verify(cartRepository).delete(cart);
    }

    @Test
    @DisplayName("clearing a cart that is not there is silent")
    void clearIsSilentWhenAbsent() {
        when(cartRepository.findByUserPublicId(USER)).thenReturn(Optional.empty());

        writer.clear(USER);

        verify(cartRepository, never()).delete(any());
    }

    /**
     * The guard that stops a sweep started an hour ago throwing away a basket somebody is filling
     * right now.
     */
    @Test
    @DisplayName("pruning re-checks the deadline and spares a cart that was touched since")
    void pruneRecheckSparesARevivedCart() {
        Instant cutoff = Instant.now().minus(90, ChronoUnit.DAYS);
        CartEntity revived = cartWith(SKU, 1, "100.00");
        revived.setLastActivityAt(Instant.now());
        when(cartRepository.findByUserPublicId(USER)).thenReturn(Optional.of(revived));

        assertThat(writer.deleteIfStillAbandoned(USER, cutoff)).isFalse();
        verify(cartRepository, never()).delete(any());
    }

    @Test
    @DisplayName("pruning deletes a cart that really is still abandoned")
    void pruneDeletesAStaleCart() {
        Instant cutoff = Instant.now().minus(90, ChronoUnit.DAYS);
        CartEntity stale = cartWith(SKU, 1, "100.00");
        stale.setLastActivityAt(Instant.now().minus(200, ChronoUnit.DAYS));
        when(cartRepository.findByUserPublicId(USER)).thenReturn(Optional.of(stale));

        assertThat(writer.deleteIfStillAbandoned(USER, cutoff)).isTrue();
        verify(cartRepository).delete(stale);
    }

    private CartEntity cartWith(String sku, int quantity, String price) {
        CartEntity cart = CartEntity.forUser(USER);
        cart.addItem(CartItemEntity.of(sku, quantity, new BigDecimal(price)));
        return cart;
    }
}
