// src/main/java/com/clickkart/cart/exception/CartTooLargeException.java
package com.clickkart.cart.exception;

/**
 * The cart has hit its line limit.
 *
 * <p>A blast-radius limit rather than a merchandising rule. Every cart read fans out to one Product
 * Service call per line, so an unbounded cart turns a single GET into an unbounded burst against a
 * service other customers are also using - and the cheapest way to build one is a loop.
 */
public class CartTooLargeException extends RuntimeException {
    public CartTooLargeException(String message) {
        super(message);
    }
}
