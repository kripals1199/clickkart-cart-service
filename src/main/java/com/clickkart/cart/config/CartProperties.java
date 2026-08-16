// src/main/java/com/clickkart/cart/config/CartProperties.java
package com.clickkart.cart.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Externalized settings, bound from {@code clickkart-cart-service.properties}. */
@Getter
@Setter
@ConfigurationProperties(prefix = "cart")
public class CartProperties {

    /** Shared HMAC secret. Must match Auth Service's signing key and the Gateway's. */
    private String jwtSecret;

    private String revocationKeyPrefix = "revoked:jti:";

    /**
     * Guards this service's own {@code /internal/**} - the surface Order Service reads a basket from
     * and empties it through. Blank refuses every internal caller.
     */
    private String internalApiKey;

    /** Presented when pricing a SKU against Product Service's internal API. */
    private String productServiceApiKey;

    private String allowedOrigins = "http://localhost:4200";

    /**
     * Distinct SKUs one cart may hold.
     *
     * <p>A blast-radius limit, not a merchandising rule: every cart read fans out to one Product
     * Service call per line, so an unbounded cart turns a single GET into an unbounded burst against
     * a service other customers are also using.
     */
    private int maxDistinctItems = 50;

    /** Units of any one SKU. Mirrors Order Service's per-line cap so a full cart can still check out. */
    private int maxQuantityPerItem = 100;

    /**
     * How long an untouched cart survives before the pruning job deletes it.
     *
     * <p>Storage is the least of it. A cart is a record of what somebody was thinking of buying,
     * which is personal data nobody agreed to have kept indefinitely - and keeping it serves no one
     * once it is this old, because prices and listings have moved on anyway.
     */
    private int abandonedAfterDays = 90;

    private long pruneIntervalMs = 3_600_000;

    private long pruneInitialDelayMs = 120_000;

    private int pruneBatchSize = 200;

    /** CIDRs whose {@code X-Forwarded-For} is believed. Empty means trust nothing. */
    private List<String> trustedProxyCidrs = new ArrayList<>();
}
