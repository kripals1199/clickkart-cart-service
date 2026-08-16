// src/test/java/com/clickkart/cart/scheduled/AbandonedCartPrunerTest.java
package com.clickkart.cart.scheduled;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clickkart.cart.service.CartService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AbandonedCartPrunerTest {

    @Mock private CartService cartService;

    @Test
    @DisplayName("runs a pass")
    void runsAPass() {
        when(cartService.pruneAbandonedCarts()).thenReturn(3);

        new AbandonedCartPruner(cartService).prune();

        verify(cartService).pruneAbandonedCarts();
    }

    /**
     * A scheduled method that throws is logged by Spring without the surrounding context, and the
     * schedule carries on regardless - so the exception buys nothing and costs the explanation. This
     * pins that the pruner swallows and reports rather than propagating.
     */
    @Test
    @DisplayName("a failure is contained rather than propagated")
    void failureIsContained() {
        when(cartService.pruneAbandonedCarts()).thenThrow(new IllegalStateException("database down"));

        assertThatCode(() -> new AbandonedCartPruner(cartService).prune()).doesNotThrowAnyException();
    }
}
