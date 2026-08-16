// src/test/java/com/clickkart/cart/serviceImpl/CartServiceImplTest.java
package com.clickkart.cart.serviceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clickkart.cart.config.CartProperties;
import com.clickkart.cart.dto.request.AddItemRequest;
import com.clickkart.cart.dto.request.SetQuantityRequest;
import com.clickkart.cart.dto.response.CartResponse;
import com.clickkart.cart.entity.CartEntity;
import com.clickkart.cart.entity.CartItemEntity;
import com.clickkart.cart.exception.CartItemNotFoundException;
import com.clickkart.cart.exception.SkuNotAddableException;
import com.clickkart.cart.feign.AvailabilityApiResponse;
import com.clickkart.cart.feign.InventoryServiceClient;
import com.clickkart.cart.feign.ProductServiceClient;
import com.clickkart.cart.feign.VariantApiResponse;
import com.clickkart.cart.repository.CartRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Behaviour this service must not lose.
 *
 * <p>The standing caveat, unchanged since Inventory Service lost a reservation status to it: a
 * mocked repository proves nothing about persistence. What is pinned here is decision-making - what
 * the cart asks the catalog, what it refuses, and above all what it does when a price moves.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CartServiceImplTest {

    private static final String USER = "usr_alice";
    private static final String CORR = "corr-1";
    private static final String SKU = "SKU-A";

    @Mock private CartRepository cartRepository;
    @Mock private CartWriter cartWriter;
    @Mock private ProductServiceClient productServiceClient;
    @Mock private InventoryServiceClient inventoryServiceClient;

    private CartServiceImpl service;

    @BeforeEach
    void setUp() {
        CartProperties properties = new CartProperties();
        properties.setProductServiceApiKey("prd-key");
        properties.setMaxDistinctItems(50);
        properties.setMaxQuantityPerItem(100);
        properties.setAbandonedAfterDays(90);
        properties.setPruneBatchSize(200);

        service = new CartServiceImpl(
                cartRepository, cartWriter, productServiceClient, inventoryServiceClient, properties);

        when(inventoryServiceClient.availability(anyString(), anyString()))
                .thenReturn(new AvailabilityApiResponse(true, new AvailabilityApiResponse.Data(SKU, true, "IN_STOCK")));
    }

    // ================================================================= pricing

    @Nested
    @DisplayName("pricing: the decision this service exists to get right")
    class Pricing {

        /**
         * The heart of it. Cart shows the live price because Order Service charges the live price;
         * a frozen one would mean the basket says 100 and the bill says 120.
         */
        @Test
        @DisplayName("renders the live catalog price, not the one stored when the item was added")
        void rendersLivePrice() {
            cartHolds(SKU, 2, "100.00");
            catalogSays(SKU, "120.00");

            CartResponse cart = service.getCart(USER, CORR);

            assertThat(cart.items()).singleElement().satisfies(line -> {
                assertThat(line.unitPrice()).isEqualByComparingTo("120.00");
                assertThat(line.lineTotal()).isEqualByComparingTo("240.00");
                assertThat(line.priceWhenAdded()).isEqualByComparingTo("100.00");
            });
            assertThat(cart.subtotal()).isEqualByComparingTo("240.00");
        }

        @Test
        @DisplayName("flags that the price moved since it went in the basket")
        void flagsPriceChange() {
            cartHolds(SKU, 1, "100.00");
            catalogSays(SKU, "120.00");

            assertThat(service.getCart(USER, CORR).items().get(0).priceChanged()).isTrue();
        }

        @Test
        @DisplayName("an unchanged price is not reported as a change")
        void unchangedPriceIsNotFlagged() {
            cartHolds(SKU, 1, "100.00");
            catalogSays(SKU, "100.00");

            assertThat(service.getCart(USER, CORR).items().get(0).priceChanged()).isFalse();
        }

        /**
         * BigDecimal.equals says 100.0 differs from 100.00. Comparing that way would tell every
         * customer their price had risen the moment the catalog changed scale.
         */
        @Test
        @DisplayName("a scale change is not a price change")
        void scaleChangeIsNotAPriceChange() {
            cartHolds(SKU, 1, "100.0");
            catalogSays(SKU, "100.00");

            assertThat(service.getCart(USER, CORR).items().get(0).priceChanged()).isFalse();
        }

        @Test
        @DisplayName("increasing a line does not refresh the recorded price, so the change stays visible")
        void incrementKeepsTheOriginalPrice() {
            CartEntity cart = cartWith(item(SKU, 1, new BigDecimal("100.00")));
            when(cartRepository.findByUserPublicId(USER)).thenReturn(Optional.of(cart));
            catalogSays(SKU, "120.00");
            when(cartWriter.addOrIncrement(anyString(), anyString(), anyInt(), any(), anyInt(), anyInt()))
                    .thenReturn(cart);

            service.addItem(USER, new AddItemRequest(SKU, 1), CORR);

            // The writer decides whether to refresh; this pins that the service hands it the live
            // price only for a NEW line, and CartWriterTest pins that an increment ignores it.
            verify(cartWriter).addOrIncrement(
                    eq(USER), eq(SKU), eq(1), eq(new BigDecimal("120.00")), anyInt(), anyInt());
        }
    }

    // ================================================================= degradation

    @Nested
    @DisplayName("when the catalog cannot be reached")
    class Degradation {

        /**
         * The opposite choice from Order Service, deliberately: an order that cannot be priced must
         * not exist, but a customer who cannot see prices for a moment is better served by a visible
         * basket than an error page.
         */
        @Test
        @DisplayName("the cart still renders, with the line marked unpriced")
        void rendersWithoutPrices() {
            cartHolds(SKU, 2, "100.00");
            when(productServiceClient.resolveVariant(anyString(), anyString(), anyString()))
                    .thenThrow(new IllegalStateException("product service down"));

            CartResponse cart = service.getCart(USER, CORR);

            assertThat(cart.items()).singleElement().satisfies(line -> {
                assertThat(line.quantity()).isEqualTo(2);
                assertThat(line.unitPrice()).isNull();
                assertThat(line.purchasable()).isFalse();
            });
            assertThat(cart.pricingDegraded()).isTrue();
            assertThat(cart.readyForCheckout()).isFalse();
        }

        @Test
        @DisplayName("an availability outage does not cost the customer their prices")
        void availabilityFailureIsSwallowed() {
            cartHolds(SKU, 1, "100.00");
            catalogSays(SKU, "100.00");
            when(inventoryServiceClient.availability(anyString(), anyString()))
                    .thenThrow(new IllegalStateException("inventory down"));

            CartResponse cart = service.getCart(USER, CORR);

            assertThat(cart.items().get(0).unitPrice()).isEqualByComparingTo("100.00");
            assertThat(cart.items().get(0).availability()).isNull();
            assertThat(cart.readyForCheckout()).isTrue();
        }
    }

    // ================================================================= stale lines

    @Nested
    @DisplayName("items the catalog will no longer sell")
    class StaleLines {

        @Test
        @DisplayName("a delisted item stays in the cart, flagged, rather than vanishing")
        void delistedItemStaysVisible() {
            cartHolds(SKU, 1, "100.00");
            when(productServiceClient.resolveVariant(eq(SKU), anyString(), anyString()))
                    .thenReturn(new VariantApiResponse(true, new VariantApiResponse.Data(
                            SKU, "PRD-1", "Thing", "Blue", "usr_seller",
                            new BigDecimal("100.00"), null, true, false, "This product is not currently on sale")));

            CartResponse cart = service.getCart(USER, CORR);

            assertThat(cart.items()).hasSize(1);
            assertThat(cart.items().get(0).purchasable()).isFalse();
            assertThat(cart.items().get(0).unpurchasableReason()).isEqualTo("This product is not currently on sale");
        }

        @Test
        @DisplayName("an unsellable line keeps the cart out of checkout and out of the subtotal")
        void unsellableLineBlocksCheckout() {
            CartEntity cart = cartWith(
                    item(SKU, 1, new BigDecimal("100.00")), item("SKU-B", 1, new BigDecimal("50.00")));
            when(cartRepository.findByUserPublicId(USER)).thenReturn(Optional.of(cart));
            catalogSays(SKU, "100.00");
            when(productServiceClient.resolveVariant(eq("SKU-B"), anyString(), anyString()))
                    .thenReturn(new VariantApiResponse(true, new VariantApiResponse.Data(
                            "SKU-B", null, null, null, null, null, null, false, false, "No such SKU")));

            CartResponse response = service.getCart(USER, CORR);

            assertThat(response.readyForCheckout()).isFalse();
            // Only the sellable line counts - quoting a total checkout cannot honour is worse than
            // quoting a smaller one.
            assertThat(response.subtotal()).isEqualByComparingTo("100.00");
        }
    }

    // ================================================================= adding

    @Nested
    @DisplayName("adding")
    class Adding {

        @Test
        @DisplayName("something the catalog will not sell is refused at the door")
        void refusesUnsellableSku() {
            when(productServiceClient.resolveVariant(eq(SKU), anyString(), anyString()))
                    .thenReturn(new VariantApiResponse(true, new VariantApiResponse.Data(
                            SKU, "PRD-1", "Thing", "Blue", "usr_seller",
                            new BigDecimal("10.00"), null, true, false, "This product is not currently on sale")));

            assertThatThrownBy(() -> service.addItem(USER, new AddItemRequest(SKU, 1), CORR))
                    .isInstanceOf(SkuNotAddableException.class)
                    .hasMessage("This product is not currently on sale");

            verify(cartWriter, never()).addOrIncrement(anyString(), anyString(), anyInt(), any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("an unknown SKU is refused")
        void refusesUnknownSku() {
            when(productServiceClient.resolveVariant(eq(SKU), anyString(), anyString()))
                    .thenReturn(new VariantApiResponse(true, new VariantApiResponse.Data(
                            SKU, null, null, null, null, null, null, false, false, "No such SKU")));

            assertThatThrownBy(() -> service.addItem(USER, new AddItemRequest(SKU, 1), CORR))
                    .isInstanceOf(SkuNotAddableException.class);
        }

        @Test
        @DisplayName("a purchasable item with no price is refused rather than added at zero")
        void refusesUnpricedSku() {
            when(productServiceClient.resolveVariant(eq(SKU), anyString(), anyString()))
                    .thenReturn(new VariantApiResponse(true, new VariantApiResponse.Data(
                            SKU, "PRD-1", "Thing", "Blue", "usr_seller", null, null, true, true, null)));

            assertThatThrownBy(() -> service.addItem(USER, new AddItemRequest(SKU, 1), CORR))
                    .isInstanceOf(SkuNotAddableException.class)
                    .hasMessageContaining("not currently priced");
        }
    }

    // ================================================================= editing

    @Nested
    @DisplayName("editing")
    class Editing {

        @Test
        @DisplayName("setting a quantity on an item that is not in the cart is a 404, not a silent add")
        void setQuantityOnMissingItem() {
            when(cartRepository.findByUserPublicId(USER)).thenReturn(Optional.of(cartWith()));

            assertThatThrownBy(() -> service.setQuantity(USER, SKU, new SetQuantityRequest(3), CORR))
                    .isInstanceOf(CartItemNotFoundException.class);
        }

        @Test
        @DisplayName("removing something that is not there is a 404")
        void removeMissingItem() {
            when(cartRepository.findByUserPublicId(USER)).thenReturn(Optional.of(cartWith()));

            assertThatThrownBy(() -> service.removeItem(USER, SKU, CORR))
                    .isInstanceOf(CartItemNotFoundException.class);
        }

        @Test
        @DisplayName("setting a quantity to zero removes the line rather than failing")
        void zeroRemoves() {
            cartHolds(SKU, 3, "100.00");
            when(cartWriter.setQuantity(USER, SKU, 0)).thenReturn(cartWith());

            CartResponse cart = service.setQuantity(USER, SKU, new SetQuantityRequest(0), CORR);

            verify(cartWriter).setQuantity(USER, SKU, 0);
            assertThat(cart.items()).isEmpty();
        }
    }

    // ================================================================= reads

    @Nested
    @DisplayName("reads")
    class Reads {

        @Test
        @DisplayName("a customer with no cart gets an empty one, and no row is created")
        void emptyCartCreatesNothing() {
            when(cartRepository.findByUserPublicId(USER)).thenReturn(Optional.empty());

            CartResponse cart = service.getCart(USER, CORR);

            assertThat(cart.items()).isEmpty();
            assertThat(cart.readyForCheckout()).isFalse();
            assertThat(cart.subtotal()).isEqualByComparingTo("0.00");
            verify(cartWriter, never()).addOrIncrement(anyString(), anyString(), anyInt(), any(), anyInt(), anyInt());
        }

        /** What Order Service sees. A price crossing this boundary would be a price a customer could edit. */
        @Test
        @DisplayName("the internal contents carry SKUs and quantities and no prices at all")
        void internalContentsCarryNoPrices() {
            cartHolds(SKU, 4, "100.00");

            var contents = service.getContents(USER);

            assertThat(contents.userPublicId()).isEqualTo(USER);
            assertThat(contents.items()).singleElement().satisfies(line -> {
                assertThat(line.sku()).isEqualTo(SKU);
                assertThat(line.quantity()).isEqualTo(4);
            });
            // The record has exactly two components; there is nowhere for a price to hide.
            assertThat(contents.items().get(0).getClass().getRecordComponents()).hasSize(2);
            verify(productServiceClient, never()).resolveVariant(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("an absent cart reads as empty for Order Service too")
        void internalContentsForMissingCart() {
            when(cartRepository.findByUserPublicId(USER)).thenReturn(Optional.empty());

            assertThat(service.getContents(USER).items()).isEmpty();
        }
    }

    // ================================================================= pruning

    @Nested
    @DisplayName("pruning")
    class Pruning {

        @Test
        @DisplayName("deletes abandoned carts and counts only what actually went")
        void prunesAbandoned() {
            CartEntity stale = cartWith();
            stale.setUserPublicId("usr_gone");
            CartEntity revived = cartWith();
            revived.setUserPublicId("usr_back");
            when(cartRepository.findAbandoned(any(), any())).thenReturn(List.of(stale, revived));
            when(cartWriter.deleteIfStillAbandoned(eq("usr_gone"), any())).thenReturn(true);
            // Came back between being listed and being deleted - the writer re-checks and declines.
            when(cartWriter.deleteIfStillAbandoned(eq("usr_back"), any())).thenReturn(false);

            assertThat(service.pruneAbandonedCarts()).isEqualTo(1);
        }
    }

    // ================================================================= fixtures

    private void cartHolds(String sku, int quantity, String priceWhenAdded) {
        CartEntity cart = cartWith(item(sku, quantity, new BigDecimal(priceWhenAdded)));
        when(cartRepository.findByUserPublicId(USER)).thenReturn(Optional.of(cart));
    }

    private void catalogSays(String sku, String price) {
        when(productServiceClient.resolveVariant(eq(sku), anyString(), anyString()))
                .thenReturn(new VariantApiResponse(true, new VariantApiResponse.Data(
                        sku, "PRD-1", "Thing", "Blue", "usr_seller",
                        new BigDecimal(price), new BigDecimal(price), true, true, null)));
    }

    private CartEntity cartWith(CartItemEntity... items) {
        CartEntity cart = CartEntity.forUser(USER);
        cart.setLastActivityAt(Instant.now());
        for (CartItemEntity item : items) {
            cart.addItem(item);
        }
        return cart;
    }

    private CartItemEntity item(String sku, int quantity, BigDecimal priceWhenAdded) {
        return CartItemEntity.of(sku, quantity, priceWhenAdded);
    }
}
