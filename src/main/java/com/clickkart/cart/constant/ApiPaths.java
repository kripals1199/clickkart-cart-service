// src/main/java/com/clickkart/cart/constant/ApiPaths.java
package com.clickkart.cart.constant;

/**
 * Route strings, grouped by who may call them.
 *
 * <p>Two audiences only: a customer acts on their own cart under {@link #BASE}, and Order Service
 * reads or empties it under {@link #INTERNAL_BASE}. There is no seller view and no admin view - a
 * cart is not evidence of anything and nobody has a reason to look inside someone else's.
 *
 * <p><strong>No user id appears in any customer-facing path.</strong> The cart is always the token's
 * own, so "someone else's cart" is not expressible. That is why {@link #BASE} is singular: there is
 * exactly one cart per person and the route names it directly rather than making the client hold an
 * id it could get wrong.
 */
public final class ApiPaths {

    private ApiPaths() {}

    public static final String BASE = "/api/v1/cart";
    public static final String ITEMS = BASE + "/items";
    public static final String ITEM_BY_SKU = ITEMS + "/{sku}";

    /**
     * Service-to-service. The user id <em>does</em> appear here, because Order Service acts on the
     * platform's behalf during a checkout and has no customer token to derive it from.
     */
    public static final String INTERNAL_BASE = "/internal/v1/carts";
    public static final String INTERNAL_WILDCARD = "/internal/**";
    public static final String INTERNAL_CART = INTERNAL_BASE + "/{userPublicId}";

    public static final String ACTUATOR_HEALTH = "/actuator/health";
    public static final String ACTUATOR_HEALTH_WILDCARD = "/actuator/health/**";
    public static final String ACTUATOR_PROMETHEUS = "/actuator/prometheus";
    public static final String SWAGGER_UI = "/swagger-ui.html";
    public static final String SWAGGER_UI_WILDCARD = "/swagger-ui/**";
    public static final String API_DOCS_WILDCARD = "/v3/api-docs/**";
}
