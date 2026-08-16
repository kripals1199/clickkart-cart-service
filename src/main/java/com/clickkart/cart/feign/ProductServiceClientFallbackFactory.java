// clickkart-cart-service/src/main/java/com/clickkart/cart/feign/ProductServiceClientFallbackFactory.java
package com.clickkart.cart.feign;

import com.clickkart.cart.exception.DownstreamServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Product Service is a <strong>required</strong> dependency of checkout, and the fallback says so by
 * failing rather than degrading.
 *
 * <p>There is no sensible degraded answer to "what does this cost". Guessing a price, reusing a
 * cached one, or letting the line through unpriced would each mean charging a customer a number the
 * catalog never authorised. A 503 tells them to try again in a moment, which is true and costs
 * nobody anything.
 *
 * <p>Note this is the opposite call from Inventory Service's use of the same client: there the
 * question is "whose SKU is this", asked once when a stock row is created and deliberately kept off
 * the reservation path. Here it is asked on every checkout, because the answer - the price - can
 * change between one checkout and the next.
 */
@Slf4j
@Component
public class ProductServiceClientFallbackFactory implements FallbackFactory<ProductServiceClient> {

    private static final String SERVICE_NAME = "Product Service";

    @Override
    public ProductServiceClient create(Throwable cause) {
        return (sku, correlationId, apiKey) -> {
            log.warn("PRICE_LOOKUP_UNAVAILABLE sku={} correlationId={} cause={}", sku, correlationId, cause.toString());
            throw new DownstreamServiceUnavailableException(SERVICE_NAME, cause);
        };
    }
}
