// src/main/java/com/clickkart/cart/controller/InternalCartController.java
package com.clickkart.cart.controller;

import com.clickkart.cart.constant.ApiPaths;
import com.clickkart.cart.constant.MdcKeys;
import com.clickkart.cart.dto.ApiResponse;
import com.clickkart.cart.dto.response.CartContentsResponse;
import com.clickkart.cart.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * The seam Order Service checks out through. Shared-secret authenticated, no Gateway route.
 *
 * <p>Two operations and no more: read the basket, and empty it once it has become an order. Cart
 * deliberately exposes no way for another service to <em>modify</em> a basket - only the person it
 * belongs to may put things in it, and an internal API that could add lines would be a way to put
 * items in a stranger's cart from inside the cluster.
 *
 * <p>The user id appears in these paths, unlike every customer-facing route, because Order Service
 * acts on the platform's behalf during a checkout and holds no customer token to derive it from.
 * That is exactly why the shared secret is the whole of the access control here, and why it is a
 * different key from every other service's.
 */
@Tag(name = "Internal", description = "Service-to-service. Not routed through the Gateway.")
@RestController
@RequiredArgsConstructor
public class InternalCartController {

    private final CartService cartService;

    /**
     * SKUs and quantities only - no prices. Order Service prices against the catalog itself, because
     * a price arriving from a basket the customer can edit is a price the customer can choose.
     */
    @Operation(summary = "Read a customer's basket for checkout")
    @GetMapping(ApiPaths.INTERNAL_CART)
    public ResponseEntity<ApiResponse<CartContentsResponse>> getContents(
            @PathVariable String userPublicId, HttpServletRequest request) {
        return envelope(HttpStatus.OK.value(), cartService.getContents(userPublicId), request);
    }

    /**
     * Empties a basket that has become an order.
     *
     * <p>Returns 204 whether or not there was anything to empty. By the time Order Service calls
     * this the order exists and its stock is held, so the only useful behaviour is to succeed
     * quietly - a 404 for an already-empty cart would turn a duplicate call into an error on a path
     * that has nothing left to roll back.
     */
    @Operation(summary = "Empty a basket after a successful checkout")
    @DeleteMapping(ApiPaths.INTERNAL_CART)
    public ResponseEntity<Void> clearAfterCheckout(@PathVariable String userPublicId) {
        cartService.clearAfterCheckout(userPublicId);
        return ResponseEntity.noContent().build();
    }

    private <T> ResponseEntity<ApiResponse<T>> envelope(int status, T data, HttpServletRequest request) {
        String correlationId = MDC.get(MdcKeys.CORRELATION_ID);
        return ResponseEntity.status(status)
                .body(ApiResponse.success(status, data, request.getRequestURI(), correlationId));
    }
}
