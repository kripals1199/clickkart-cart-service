// src/main/java/com/clickkart/cart/exception/CartItemNotFoundException.java
package com.clickkart.cart.exception;

/** A SKU the caller asked to change or remove is not in their cart. */
public class CartItemNotFoundException extends RuntimeException {
    public CartItemNotFoundException(String message) {
        super(message);
    }
}
