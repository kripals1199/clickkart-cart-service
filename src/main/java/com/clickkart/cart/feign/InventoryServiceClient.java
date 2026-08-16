// src/main/java/com/clickkart/cart/feign/InventoryServiceClient.java
package com.clickkart.cart.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Inventory Service's <strong>public</strong> availability endpoint - the banded one, not the
 * internal reservation surface.
 *
 * <p>No API key, because none is needed: this is the same endpoint an anonymous browser calls, and
 * it answers in bands ({@code OUT_OF_STOCK} / {@code LOW} / {@code IN_STOCK}) precisely so an exact
 * count is never disclosed. A cart showing "only 2 left" would be repeating a number Inventory
 * deliberately withholds; a cart showing "low stock" tells the customer exactly what the product page
 * already does.
 *
 * <p>Cart holds the <em>weakest</em> possible relationship with Inventory on this platform: it reads
 * a hint and can reserve nothing. That is the point. If a cart could hold stock, browsing would take
 * goods off sale.
 */
@FeignClient(name = InventoryServiceClient.SERVICE_NAME)
public interface InventoryServiceClient {

    String SERVICE_NAME = "clickkart-inventory-service";
    String AVAILABILITY_PATH = "/api/v1/inventory/availability/{sku}";
    String CORRELATION_ID_HEADER = "X-Correlation-Id";

    @GetMapping(AVAILABILITY_PATH)
    AvailabilityApiResponse availability(
            @PathVariable("sku") String sku, @RequestHeader(CORRELATION_ID_HEADER) String correlationId);
}
