// src/main/java/com/clickkart/cart/scheduled/AbandonedCartPruner.java
package com.clickkart.cart.scheduled;

import com.clickkart.cart.constant.LoggerNames;
import com.clickkart.cart.service.CartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes carts nobody has touched in a long time.
 *
 * <p>The platform's third scheduled job, and the only one that is not about correctness. Inventory's
 * sweeper stops stock leaking; Order's stops checkouts hanging open; Payment's reconciler chases
 * money that moved without anyone knowing. Nothing breaks if this one never runs - which is exactly
 * why it is worth being explicit about why it exists at all.
 *
 * <p>A cart is a record of what somebody was thinking of buying. That is personal data nobody agreed
 * to have kept indefinitely, and it stops being useful to them long before it stops being a liability
 * for the shop: a three-year-old basket holds prices that have moved and listings that are gone.
 * Deleting it serves the customer as much as the disk.
 *
 * <p>Hourly rather than by the minute. Nothing here is time-critical, and a job that only reclaims
 * space has no business competing for database connections with the ones that take money.
 */
@Slf4j(topic = LoggerNames.SECURITY)
@Component
@RequiredArgsConstructor
public class AbandonedCartPruner {

    private final CartService cartService;

    @Scheduled(
            fixedDelayString = "${cart.prune-interval-ms:3600000}",
            initialDelayString = "${cart.prune-initial-delay-ms:120000}")
    public void prune() {
        try {
            int pruned = cartService.pruneAbandonedCarts();
            if (pruned > 0) {
                log.info("ABANDONED_CART_PRUNE_COMPLETED pruned={}", pruned);
            }
        } catch (RuntimeException e) {
            // Caught rather than propagated: an uncaught exception from a @Scheduled method is logged
            // by Spring without the surrounding context, and a pruner that dies quietly is one nobody
            // notices has stopped - which, for a job whose only symptom is a slowly growing table, is
            // the difference between a log line and a disk alert two years later.
            log.error("ABANDONED_CART_PRUNE_FAILED - old carts remain and will be retried next run", e);
        }
    }
}
