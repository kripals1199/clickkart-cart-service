// src/main/java/com/clickkart/cart/service/CartService.java
package com.clickkart.cart.service;

import com.clickkart.cart.dto.request.AddItemRequest;
import com.clickkart.cart.dto.request.SetQuantityRequest;
import com.clickkart.cart.dto.response.CartContentsResponse;
import com.clickkart.cart.dto.response.CartResponse;

public interface CartService {

    /** The customer's basket, priced live from the catalog. */
    CartResponse getCart(String userPublicId, String correlationId);

    CartResponse addItem(String userPublicId, AddItemRequest request, String correlationId);

    CartResponse setQuantity(String userPublicId, String sku, SetQuantityRequest request, String correlationId);

    CartResponse removeItem(String userPublicId, String sku, String correlationId);

    CartResponse clear(String userPublicId, String correlationId);

    /** SKUs and quantities for Order Service. No prices - see {@link CartContentsResponse}. */
    CartContentsResponse getContents(String userPublicId);

    /**
     * Empties a cart that has become an order.
     *
     * <p>Separate from {@link #clear} because the caller is Order Service rather than the customer,
     * and because it must not fail loudly: by the time it runs, the order exists and the stock is
     * held, so refusing here would strand a successful checkout behind a housekeeping error.
     */
    void clearAfterCheckout(String userPublicId);

    /** Deletes carts nobody has touched in a long time. Returns how many went. */
    int pruneAbandonedCarts();
}
