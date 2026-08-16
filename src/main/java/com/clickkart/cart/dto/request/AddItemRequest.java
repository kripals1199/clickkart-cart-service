// src/main/java/com/clickkart/cart/dto/request/AddItemRequest.java
package com.clickkart.cart.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Putting something in the basket.
 *
 * <p>Adding a SKU already in the cart <strong>increases the quantity</strong> rather than being
 * refused - the opposite of Order Service's checkout, which rejects a duplicated SKU outright. The
 * difference is deliberate and follows from what each request means. A checkout request is a
 * statement of exactly what to buy, so silently merging two lines would answer a different request
 * from the one that was sent. Pressing "add to cart" twice is not a statement; it is a person asking
 * for another one, and a shop that answered it with an error would be broken.
 */
public record AddItemRequest(
        @NotBlank(message = "must not be blank") @Size(max = 60, message = "must be at most 60 characters") String sku,
        @Min(value = 1, message = "must be at least 1") @Max(value = 100, message = "must be at most 100") int quantity) {}
