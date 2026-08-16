// clickkart-cart-service/src/main/java/com/clickkart/cart/config/OpenApiConfig.java
package com.clickkart.cart.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    /**
     * The bearer scheme <em>is</em> applied globally here, unlike in Category, Product and Inventory
     * Service, and for the same reason those three do not: this API has no public surface. Every
     * documented route needs a token, so saying so once at the top is both accurate and the honest
     * thing to show an integrator.
     *
     * <p>The internal payment callback is excluded from the document entirely by {@code
     * springdoc.paths-to-exclude=/internal/**} - it is authenticated by a shared secret rather than a
     * bearer token, and publishing it here would advertise a surface no external client can use.
     */
    @Bean
    public OpenAPI orderServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ClickKart Order Service")
                        .version("1.0.0")
                        .description("The order lifecycle: checkout prices against the catalog and holds stock, "
                                + "payment confirms or releases it, sellers fulfil their own lines, and unpaid "
                                + "orders expire."))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
