// clickkart-cart-service/src/main/java/com/clickkart/cart/dto/PageResponse.java
package com.clickkart.cart.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Lean pagination contract for the API surface - Spring Data's own {@code Page<T>} serializes
 * with a lot of internal detail (a nested {@code pageable}, {@code sort}, {@code numberOfElements},
 * duplicate {@code empty}/{@code first}/{@code last} booleans in awkward places) that's normal
 * for a backend-to-backend call but noisy for a UI integrator. This is just the 6 fields a
 * frontend actually needs.
 */
public record PageResponse<T>(
        List<T> content, int page, int size, long totalElements, int totalPages, boolean first, boolean last) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
