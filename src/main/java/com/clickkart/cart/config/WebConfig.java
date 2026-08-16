// clickkart-cart-service/src/main/java/com/clickkart/cart/config/WebConfig.java
package com.clickkart.cart.config;

import com.clickkart.cart.constant.ApiPaths;
import com.clickkart.cart.filter.AccessLogFilter;
import com.clickkart.cart.filter.CorrelationIdFilter;
import com.clickkart.cart.filter.MdcCleanupFilter;
import com.clickkart.cart.security.CorrelationIdGenerator;
import java.util.List;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * This service mints a provisional correlation id when one is absent - but for a different reason
 * from the services it copied the filter from, and the difference is worth being precise about
 * since it is the sort of thing that gets carried forward unexamined.
 *
 * <p>Category, Product and Inventory mint because an anonymous browser reaches their public catalog
 * with no token to have taken an id from. This service has no public surface at all, so that
 * justification does not apply here. What does apply is ordering: {@code AccessLogFilter} runs
 * before authentication, so a request that is about to be rejected as unauthenticated still has to
 * be traceable - and a rejected request is exactly the one somebody will later need to find in the
 * logs. Requiring the header instead would also break direct access, where a valid caller presents a
 * bearer token and no header at all, and direct access is a case the architecture requires every
 * service to handle.
 *
 * <p>Rule 13 still holds for everything downstream of authentication: {@code
 * JwtAuthenticationFilter} overwrites the MDC value with the {@code correlationId} claim from the
 * verified token, and {@code InternalApiKeyFilter} requires the header outright, so anything this
 * service goes on to do is traced by Auth Service's id and never by one invented here. The minted
 * value survives only for requests that never got that far.
 */
@Configuration
public class WebConfig {

    private static final List<String> CORRELATION_ID_EXEMPT_PATHS = List.of(
            ApiPaths.ACTUATOR_HEALTH,
            ApiPaths.ACTUATOR_HEALTH_WILDCARD,
            ApiPaths.ACTUATOR_PROMETHEUS,
            ApiPaths.SWAGGER_UI,
            ApiPaths.SWAGGER_UI_WILDCARD,
            ApiPaths.API_DOCS_WILDCARD);

    @Bean
    public FilterRegistrationBean<MdcCleanupFilter> mdcCleanupFilter() {
        FilterRegistrationBean<MdcCleanupFilter> registration = new FilterRegistrationBean<>(new MdcCleanupFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter(
            CorrelationIdGenerator correlationIdGenerator) {
        FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>(
                new CorrelationIdFilter(correlationIdGenerator, CORRELATION_ID_EXEMPT_PATHS));
        // Before the access log, so REQUEST_START already carries the id.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    public FilterRegistrationBean<AccessLogFilter> accessLogFilter() {
        FilterRegistrationBean<AccessLogFilter> registration = new FilterRegistrationBean<>(new AccessLogFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
