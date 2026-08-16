// src/main/java/com/clickkart/cart/serviceImpl/CartServiceImpl.java
package com.clickkart.cart.serviceImpl;

import com.clickkart.cart.config.CartProperties;
import java.time.Instant;
import com.clickkart.cart.constant.LoggerNames;
import com.clickkart.cart.dto.request.AddItemRequest;
import com.clickkart.cart.dto.request.SetQuantityRequest;
import com.clickkart.cart.dto.response.CartContentsResponse;
import com.clickkart.cart.dto.response.CartItemResponse;
import com.clickkart.cart.dto.response.CartResponse;
import com.clickkart.cart.entity.CartEntity;
import com.clickkart.cart.entity.CartItemEntity;
import com.clickkart.cart.exception.CartItemNotFoundException;
import com.clickkart.cart.exception.CartTooLargeException;
import com.clickkart.cart.exception.SkuNotAddableException;
import com.clickkart.cart.feign.AvailabilityApiResponse;
import com.clickkart.cart.feign.InventoryServiceClient;
import com.clickkart.cart.feign.ProductServiceClient;
import com.clickkart.cart.feign.VariantApiResponse;
import com.clickkart.cart.repository.CartRepository;
import com.clickkart.cart.service.CartService;
import java.math.BigDecimal;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Basket state, plus live enrichment from the catalog.
 *
 * <p><strong>The cart owns quantities and nothing else.</strong> Names, prices, sellers and
 * availability are read from Product and Inventory every time the cart is rendered, and none of them
 * is stored. That costs a fan-out per read and buys a basket that is never quietly wrong - which is
 * the right trade for a screen whose entire job is to tell someone what they are about to be charged.
 *
 * <p><strong>Enrichment is best-effort, per line.</strong> If Product Service cannot be reached the
 * cart still renders, with those lines marked unpriced and the response flagged {@code
 * pricingDegraded}. This is a deliberate departure from Order Service, which treats the same
 * dependency as required and fails the checkout: an order that cannot be priced must not exist,
 * whereas a customer who cannot see prices for a moment is better served by a visible basket than an
 * error page. The two services take opposite positions on the same call because they are answering
 * different questions.
 */
@Slf4j(topic = LoggerNames.SECURITY)
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartWriter cartWriter;
    private final ProductServiceClient productServiceClient;
    private final InventoryServiceClient inventoryServiceClient;
    private final CartProperties cartProperties;

    // ------------------------------------------------------------------ reads

    @Override
    @Transactional(readOnly = true)
    public CartResponse getCart(String userPublicId, String correlationId) {
        return cartRepository
                .findByUserPublicId(userPublicId)
                .map(cart -> render(cart, correlationId))
                // No row until something is added. A GET must not create one, or every health probe
                // and every curious click would leave a cart behind to be pruned later.
                .orElseGet(() -> CartResponse.empty(null));
    }

    @Override
    @Transactional(readOnly = true)
    public CartContentsResponse getContents(String userPublicId) {
        return cartRepository
                .findByUserPublicId(userPublicId)
                .map(CartContentsResponse::from)
                .orElseGet(() -> CartContentsResponse.empty(userPublicId));
    }

    // ------------------------------------------------------------------ writes

    @Override
    public CartResponse addItem(String userPublicId, AddItemRequest request, String correlationId) {
        // Checked before the write, so a basket never accepts something the catalog will not sell.
        VariantApiResponse variant = resolveOrRefuse(request.sku(), correlationId);
        BigDecimal price = variant.data().sellingPrice();

        CartEntity cart;
        try {
            cart = cartWriter.addOrIncrement(
                    userPublicId,
                    request.sku(),
                    request.quantity(),
                    price,
                    cartProperties.getMaxDistinctItems(),
                    cartProperties.getMaxQuantityPerItem());
        } catch (DataIntegrityViolationException e) {
            // Two first-ever adds racing each other; the unique constraint on user_public_id let one
            // through. Re-reading is safe only because the writer committed in its own transaction -
            // sharing this thread's would have marked it rollback-only and this retry would fail too.
            log.warn("CART_CREATE_RACE user={} - retrying against the winning cart", userPublicId);
            cart = cartWriter.addOrIncrement(
                    userPublicId,
                    request.sku(),
                    request.quantity(),
                    price,
                    cartProperties.getMaxDistinctItems(),
                    cartProperties.getMaxQuantityPerItem());
        }
        return render(cart, correlationId);
    }

    @Override
    public CartResponse setQuantity(
            String userPublicId, String sku, SetQuantityRequest request, String correlationId) {

        CartEntity cart = requireCart(userPublicId);
        if (cart.findItem(sku).isEmpty()) {
            throw new CartItemNotFoundException("That item is not in your cart: " + sku);
        }
        if (request.quantity() > cartProperties.getMaxQuantityPerItem()) {
            throw new CartTooLargeException(
                    "You may have at most " + cartProperties.getMaxQuantityPerItem() + " of any one item");
        }
        // Zero removes the line rather than failing - see SetQuantityRequest.
        return render(cartWriter.setQuantity(userPublicId, sku, request.quantity()), correlationId);
    }

    @Override
    public CartResponse removeItem(String userPublicId, String sku, String correlationId) {
        CartEntity cart = requireCart(userPublicId);
        if (cart.findItem(sku).isEmpty()) {
            throw new CartItemNotFoundException("That item is not in your cart: " + sku);
        }
        return render(cartWriter.setQuantity(userPublicId, sku, 0), correlationId);
    }

    @Override
    public CartResponse clear(String userPublicId, String correlationId) {
        cartWriter.clear(userPublicId);
        return CartResponse.empty(Instant.now());
    }

    @Override
    public void clearAfterCheckout(String userPublicId) {
        cartWriter.clear(userPublicId);
    }

    // ------------------------------------------------------------------ pruning

    /**
     * Deletes carts nobody has touched in a long time.
     *
     * <p>Storage is the least of the reasons. A cart records what somebody was thinking of buying -
     * personal data nobody agreed to have kept forever, and useless to them anyway once the prices
     * and listings in it have moved on.
     *
     * <p>Safe on multiple replicas without a distributed lock: deletes are idempotent, and a replica
     * that loses the race simply deletes nothing.
     */
    @Override
    public int pruneAbandonedCarts() {
        Instant cutoff = Instant.now().minus(cartProperties.getAbandonedAfterDays(), ChronoUnit.DAYS);
        List<CartEntity> abandoned =
                cartRepository.findAbandoned(cutoff, PageRequest.of(0, cartProperties.getPruneBatchSize()));

        int pruned = 0;
        for (CartEntity cart : abandoned) {
            if (cartWriter.deleteIfStillAbandoned(cart.getUserPublicId(), cutoff)) {
                pruned++;
            }
        }
        return pruned;
    }

    // ------------------------------------------------------------------ enrichment

    /**
     * Turns stored quantities into a basket the customer can read, one catalog call per line.
     *
     * <p>Sequential rather than parallel. The cap on distinct items keeps the fan-out bounded, and a
     * thread pool here would trade a predictable number of calls for an unpredictable burst against
     * a service other customers are also using.
     */
    private CartResponse render(CartEntity cart, String correlationId) {
        List<CartItemResponse> lines = new ArrayList<>(cart.getItems().size());

        for (CartItemEntity item : cart.getItems()) {
            VariantApiResponse variant;
            try {
                variant = productServiceClient.resolveVariant(
                        item.getSku(), correlationId, cartProperties.getProductServiceApiKey());
            } catch (RuntimeException e) {
                log.warn("CART_PRICING_UNAVAILABLE sku={} correlationId={} cause={}",
                        item.getSku(), correlationId, e.toString());
                lines.add(CartItemResponse.unpriced(item));
                continue;
            }

            if (variant == null || !variant.exists()) {
                // Added while it existed, delisted since. The line stays, flagged - removing it
                // silently would leave the customer wondering what they had picked.
                lines.add(CartItemResponse.priced(
                        item, null, null, null, null, null, null, false, "This item is no longer sold", null));
                continue;
            }

            var data = variant.data();
            boolean purchasable = variant.purchasable() && data.sellingPrice() != null;
            String reason = purchasable
                    ? null
                    : (data.sellingPrice() == null && variant.purchasable()
                            ? "This item is not currently priced"
                            : variant.reason());

            lines.add(CartItemResponse.priced(
                    item,
                    data.productPublicId(),
                    data.productName(),
                    data.variantName(),
                    data.sellerPublicId(),
                    data.sellingPrice(),
                    data.mrp(),
                    purchasable,
                    reason,
                    availabilityBand(item.getSku(), correlationId)));
        }

        return CartResponse.of(lines, cart.getLastActivityAt());
    }

    /**
     * A banded stock hint, or nothing.
     *
     * <p>Swallowed on failure rather than degrading the line: availability is the least important
     * thing on a basket row, and a cart that showed no price because a stock service blinked would be
     * trading something the customer needs for something they merely like to know.
     */
    private String availabilityBand(String sku, String correlationId) {
        try {
            AvailabilityApiResponse response = inventoryServiceClient.availability(sku, correlationId);
            return response == null ? null : response.bandOrNull();
        } catch (RuntimeException e) {
            log.debug("CART_AVAILABILITY_UNAVAILABLE sku={} cause={}", sku, e.toString());
            return null;
        }
    }

    private VariantApiResponse resolveOrRefuse(String sku, String correlationId) {
        VariantApiResponse variant =
                productServiceClient.resolveVariant(sku, correlationId, cartProperties.getProductServiceApiKey());

        if (variant == null || !variant.exists()) {
            throw new SkuNotAddableException(sku, "No such SKU: " + sku);
        }
        if (!variant.purchasable()) {
            throw new SkuNotAddableException(sku, variant.reason());
        }
        if (variant.data().sellingPrice() == null) {
            throw new SkuNotAddableException(sku, "This item is not currently priced");
        }
        return variant;
    }

    private CartEntity requireCart(String userPublicId) {
        return cartRepository
                .findByUserPublicId(userPublicId)
                .orElseThrow(() -> new CartItemNotFoundException("Your cart is empty"));
    }
}
