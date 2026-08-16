// src/main/java/com/clickkart/cart/dto/request/SetQuantityRequest.java
package com.clickkart.cart.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Setting a line to an absolute quantity, for the stepper on a cart page.
 *
 * <p>Absolute rather than a delta, so a retry after a timeout is harmless: "set to 3" twice leaves 3,
 * where "+1" twice leaves 4. The same reasoning Inventory Service uses to separate setting a stock
 * level from adjusting one, and the reason both verbs exist here rather than one.
 *
 * <p>Zero is accepted and removes the line. A stepper clicked down to nothing should empty the row
 * rather than fail, and making the client notice the boundary and switch to DELETE is a rule it will
 * eventually get wrong.
 */
public record SetQuantityRequest(
        @Min(value = 0, message = "must not be negative") @Max(value = 100, message = "must be at most 100") int quantity) {}
