// src/main/java/com/clickkart/cart/controller/CartController.java
package com.clickkart.cart.controller;

import com.clickkart.cart.constant.ApiPaths;
import com.clickkart.cart.constant.MdcKeys;
import com.clickkart.cart.dto.ApiResponse;
import com.clickkart.cart.dto.request.AddItemRequest;
import com.clickkart.cart.dto.request.SetQuantityRequest;
import com.clickkart.cart.dto.response.CartResponse;
import com.clickkart.cart.security.AuthenticatedPrincipal;
import com.clickkart.cart.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The customer's own basket. Every route acts on the token's own subject, so touching someone else's
 * cart is not expressible - there is no user id in any path here.
 *
 * <p>No role annotation, deliberately. Any signed-in account may have a cart, a seller who wants to
 * buy something included; a role check would exclude people for no benefit, since ownership is what
 * actually protects a cart.
 *
 * <p>Every write returns the <strong>whole cart</strong> rather than the line that changed. A cart
 * page has to redraw its total and its checkout button after every click, and returning a fragment
 * would force a second GET on every interaction - or, worse, tempt a client into recomputing the
 * total itself and disagreeing with the server about what things cost.
 */
@Tag(name = "Cart", description = "The signed-in customer's own basket")
@RestController
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @Operation(summary = "My cart, priced live from the catalog")
    @GetMapping(ApiPaths.BASE)
    public ResponseEntity<ApiResponse<CartResponse>> getCart(
            @AuthenticationPrincipal AuthenticatedPrincipal principal, HttpServletRequest request) {
        return envelope(
                HttpStatus.OK.value(), cartService.getCart(principal.userId(), principal.correlationId()), request);
    }

    /** Adding a SKU already present increases its quantity - see {@link AddItemRequest}. */
    @Operation(summary = "Add an item, or increase one already in the cart")
    @PostMapping(ApiPaths.ITEMS)
    public ResponseEntity<ApiResponse<CartResponse>> addItem(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @Valid @RequestBody AddItemRequest body,
            HttpServletRequest request) {
        CartResponse cart = cartService.addItem(principal.userId(), body, principal.correlationId());
        return envelope(HttpStatus.OK.value(), cart, request);
    }

    /** Absolute, so a retried stepper click is harmless. Zero removes the line. */
    @Operation(summary = "Set an item's quantity")
    @PutMapping(ApiPaths.ITEM_BY_SKU)
    public ResponseEntity<ApiResponse<CartResponse>> setQuantity(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable String sku,
            @Valid @RequestBody SetQuantityRequest body,
            HttpServletRequest request) {
        CartResponse cart = cartService.setQuantity(principal.userId(), sku, body, principal.correlationId());
        return envelope(HttpStatus.OK.value(), cart, request);
    }

    @Operation(summary = "Remove an item")
    @DeleteMapping(ApiPaths.ITEM_BY_SKU)
    public ResponseEntity<ApiResponse<CartResponse>> removeItem(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable String sku,
            HttpServletRequest request) {
        CartResponse cart = cartService.removeItem(principal.userId(), sku, principal.correlationId());
        return envelope(HttpStatus.OK.value(), cart, request);
    }

    @Operation(summary = "Empty the cart")
    @DeleteMapping(ApiPaths.BASE)
    public ResponseEntity<ApiResponse<CartResponse>> clear(
            @AuthenticationPrincipal AuthenticatedPrincipal principal, HttpServletRequest request) {
        return envelope(
                HttpStatus.OK.value(), cartService.clear(principal.userId(), principal.correlationId()), request);
    }

    private <T> ResponseEntity<ApiResponse<T>> envelope(int status, T data, HttpServletRequest request) {
        String correlationId = MDC.get(MdcKeys.CORRELATION_ID);
        return ResponseEntity.status(status)
                .body(ApiResponse.success(status, data, request.getRequestURI(), correlationId));
    }
}
