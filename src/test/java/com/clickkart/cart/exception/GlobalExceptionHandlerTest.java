// src/test/java/com/clickkart/cart/exception/GlobalExceptionHandlerTest.java
package com.clickkart.cart.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.clickkart.cart.dto.ApiResponse;
import com.clickkart.cart.dto.ErrorDetail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

/** The status codes and error codes a cart client branches on. */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/cart");

    @Test
    @DisplayName("a SKU that is not in the cart is 404")
    void missingItemIsNotFound() {
        var response = handler.handleItemNotFound(
                new CartItemNotFoundException("That item is not in your cart: SKU-A"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(codeOf(response)).isEqualTo("CART_ITEM_NOT_FOUND");
    }

    /** 409 rather than 404: the SKU may well exist, the catalog just will not sell it right now. */
    @Test
    @DisplayName("something the catalog will not sell is 409 and names the SKU")
    void unsellableSkuIsConflict() {
        var response = handler.handleSkuNotAddable(
                new SkuNotAddableException("SKU-A", "This product is not currently on sale"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(detailOf(response).metadata()).containsExactly(java.util.Map.entry("sku", "SKU-A"));
        assertThat(response.getBody().getMessage()).isEqualTo("This product is not currently on sale");
    }

    @Test
    @DisplayName("a cart at its limit is 409 and says what the limit is")
    void cartTooLargeIsConflict() {
        var response = handler.handleCartTooLarge(
                new CartTooLargeException("A cart may hold at most 50 different items"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codeOf(response)).isEqualTo("CART_TOO_LARGE");
        assertThat(response.getBody().getMessage()).contains("50");
    }

    @Test
    @DisplayName("a catalog outage on the add path is 503, not 500 - it is worth retrying")
    void downstreamOutageIsRetryable() {
        var response = handler.handleDownstreamUnavailable(
                new DownstreamServiceUnavailableException("Product Service", new IllegalStateException("boom")),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(codeOf(response)).isEqualTo("SERVICE_UNAVAILABLE");
    }

    @Test
    @DisplayName("a missing token is 401 and a wrong role is 403")
    void authenticationAndAuthorizationDiffer() {
        assertThat(handler.handleBadCredentials(new BadCredentialsException("no token"), request).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(handler.handleAccessDenied(new AccessDeniedException("nope"), request).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("an internal call without a correlation id is refused")
    void missingCorrelationIdIsRefused() {
        var response = handler.handleMissingCorrelationId(
                new MissingCorrelationIdException("Request is missing the required X-Correlation-Id header"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(codeOf(response)).isEqualTo("MISSING_CORRELATION_ID");
    }

    @Test
    @DisplayName("two writes to one cart at once is 409 - retryable - rather than 500")
    void optimisticLockIsRetryable() {
        var response = handler.handleOptimisticLock(
                new org.springframework.dao.OptimisticLockingFailureException("clash"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).contains("cart");
    }

    /** An unexpected failure must never leak an internal message to a client. */
    @Test
    @DisplayName("an unhandled exception says nothing about itself")
    void unexpectedErrorIsOpaque() {
        var response = handler.handleUnexpected(
                new IllegalStateException("jdbc url user=app password=hunter2"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred");
        assertThat(response.getBody().getMessage()).doesNotContain("password");
    }

    private ErrorDetail detailOf(ResponseEntity<ApiResponse<Void>> response) {
        return (ErrorDetail) response.getBody().getError();
    }

    private String codeOf(ResponseEntity<ApiResponse<Void>> response) {
        return detailOf(response).code();
    }
}
