// clickkart-cart-service/src/main/java/com/clickkart/cart/constant/LoggerNames.java
package com.clickkart.cart.constant;

/**
 * Named loggers routed to their own dedicated appender/file by {@code logback-spring.xml}
 * (ACCESS -> access.log). Kept as constants so every {@code LoggerFactory.getLogger(...)} call
 * site matches the logback configuration exactly - a typo in either place would silently route
 * log lines to the wrong (or no) file.
 */
public final class LoggerNames {

    private LoggerNames() {}

    public static final String SECURITY = "SECURITY";
    public static final String ACCESS = "ACCESS";
    public static final String AUDIT = "AUDIT";
}
