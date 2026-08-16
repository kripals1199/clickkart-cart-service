// src/main/java/com/clickkart/cart/dto/response/CartContentsResponse.java
package com.clickkart.cart.dto.response;

import com.clickkart.cart.entity.CartEntity;
import java.util.List;

/**
 * What Order Service reads at checkout: SKUs and quantities, and nothing else.
 *
 * <p><strong>No prices cross this boundary, on purpose.</strong> Order Service prices every line
 * against Product Service itself, because the catalog is the only thing entitled to say what
 * something costs. Passing a price here would create a second route to the amount a customer is
 * charged - one that starts in a cart the customer can edit - and the whole reason checkout refuses a
 * client-supplied price is to make sure that route does not exist.
 *
 * <p>So this deliberately carries less than the customer-facing {@link CartResponse}. It is not a
 * trimmed-down version of it; it is the list of intentions with every opinion stripped out.
 */
public record CartContentsResponse(String userPublicId, List<Line> items) {

    public record Line(String sku, int quantity) {}

    public static CartContentsResponse from(CartEntity cart) {
        return new CartContentsResponse(
                cart.getUserPublicId(),
                cart.getItems().stream()
                        .map(item -> new Line(item.getSku(), item.getQuantity()))
                        .toList());
    }

    public static CartContentsResponse empty(String userPublicId) {
        return new CartContentsResponse(userPublicId, List.of());
    }
}
