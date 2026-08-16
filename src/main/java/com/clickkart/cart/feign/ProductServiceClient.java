// clickkart-cart-service/src/main/java/com/clickkart/cart/feign/ProductServiceClient.java
package com.clickkart.cart.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Product Service's internal API - the authority on what a SKU costs.
 *
 * <p>This is the call that makes a client-supplied price unnecessary, and therefore the call that
 * makes it safe to refuse one. It also answers whether the SKU may be sold at all, so a listing
 * pulled from sale between browsing and checkout is caught here rather than shipping.
 */
@FeignClient(name = ProductServiceClient.SERVICE_NAME, fallbackFactory = ProductServiceClientFallbackFactory.class)
public interface ProductServiceClient {

    String SERVICE_NAME = "clickkart-product-service";
    String VARIANT_PATH = "/internal/v1/products/variants/{sku}";
    String CORRELATION_ID_HEADER = "X-Correlation-Id";
    String API_KEY_HEADER = "X-Internal-Api-Key";

    @GetMapping(VARIANT_PATH)
    VariantApiResponse resolveVariant(
            @PathVariable("sku") String sku,
            @RequestHeader(CORRELATION_ID_HEADER) String correlationId,
            @RequestHeader(API_KEY_HEADER) String apiKey);
}
