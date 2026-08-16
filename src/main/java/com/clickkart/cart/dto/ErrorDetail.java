// clickkart-cart-service/src/main/java/com/clickkart/cart/dto/ErrorDetail.java
package com.clickkart.cart.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * The {@code error} payload inside {@link ApiResponse} on every failure response - a typed
 * shape instead of the ad-hoc {@code String}/{@code Map} mix a hand-rolled error body tends to
 * accumulate over time. {@code code} is a stable, machine-readable identifier a UI integrator
 * can {@code switch} on (e.g. {@code "ADDRESS_NOT_FOUND"}) - never the free-text {@code message}
 * field on {@link ApiResponse}, which is display text and may be reworded without notice.
 * {@code fieldErrors} is populated only for bean-validation failures; {@code metadata} only
 * when a specific error carries extra structured detail (e.g. {@code limit}). Both are
 * {@code null}/omitted otherwise.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorDetail(String code, Map<String, String> fieldErrors, Map<String, Object> metadata) {

    public static ErrorDetail of(String code) {
        return new ErrorDetail(code, null, null);
    }

    public static ErrorDetail withFieldErrors(String code, Map<String, String> fieldErrors) {
        return new ErrorDetail(code, fieldErrors, null);
    }

    public static ErrorDetail withMetadata(String code, Map<String, Object> metadata) {
        return new ErrorDetail(code, null, metadata);
    }
}
