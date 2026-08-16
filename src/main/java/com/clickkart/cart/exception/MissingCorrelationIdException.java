// clickkart-cart-service/src/main/java/com/clickkart/cart/exception/MissingCorrelationIdException.java
package com.clickkart.cart.exception;

/** Rule 13: only Auth Service mints a correlation id; every other service requires one already present. */
public class MissingCorrelationIdException extends RuntimeException {

    public MissingCorrelationIdException(String message) {
        super(message);
    }
}
