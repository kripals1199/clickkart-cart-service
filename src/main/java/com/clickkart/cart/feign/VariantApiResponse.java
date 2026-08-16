// clickkart-cart-service/src/main/java/com/clickkart/cart/feign/VariantApiResponse.java
package com.clickkart.cart.feign;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/**
 * Product Service's SKU verdict, unwrapped from the standard envelope.
 *
 * <p>{@code ignoreUnknown} on both levels so a field added there cannot break deserialization here -
 * the coupling the no-shared-library rule exists to avoid.
 *
 * <p>Unlike Inventory Service's copy of this record, the price fields <em>are</em> bound. Inventory
 * has no business holding a second copy of a price; this service has no business inventing one. The
 * money a customer is charged has to come from the catalog that advertised it, and this is the wire
 * it comes over.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VariantApiResponse(boolean success, Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(
            String sku,
            String productPublicId,
            String productName,
            String variantName,
            String sellerPublicId,
            BigDecimal sellingPrice,
            BigDecimal mrp,
            boolean exists,
            boolean purchasable,
            String reason) {}

    public boolean exists() {
        return data != null && data.exists();
    }

    public boolean purchasable() {
        return data != null && data.purchasable();
    }

    /** Product Service's own wording for why a SKU cannot be bought, passed through unaltered. */
    public String reason() {
        return data == null ? null : data.reason();
    }
}
