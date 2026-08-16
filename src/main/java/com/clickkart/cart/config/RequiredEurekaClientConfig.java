// clickkart-cart-service/src/main/java/com/clickkart/cart/config/RequiredEurekaClientConfig.java
package com.clickkart.cart.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Forces eager, fail-fast resolution of {@code eureka.client.service-url.defaultZone} - a
 * Map<String,String>-bound property Spring's relaxed Binder doesn't validate for unresolved
 * placeholders the way scalar properties are. Same pattern duplicated identically across every
 * service in this project (Rule 4: no shared library).
 */
@Configuration(proxyBeanMethods = false)
@Profile({"test", "qa", "prod"})
class RequiredEurekaCredentialsConfig {

    RequiredEurekaCredentialsConfig(
            @Value("${EUREKA_DASHBOARD_USERNAME}") String eurekaDashboardUsername,
            @Value("${EUREKA_DASHBOARD_PASSWORD}") String eurekaDashboardPassword) {
        require(eurekaDashboardUsername, "EUREKA_DASHBOARD_USERNAME");
        require(eurekaDashboardPassword, "EUREKA_DASHBOARD_PASSWORD");
    }

    static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must not be blank");
        }
    }
}

@Configuration(proxyBeanMethods = false)
@Profile("prod")
class RequiredProdEurekaHostConfig {

    RequiredProdEurekaHostConfig(@Value("${EUREKA_SERVER_HOST}") String eurekaServerHost) {
        RequiredEurekaCredentialsConfig.require(eurekaServerHost, "EUREKA_SERVER_HOST");
    }
}
