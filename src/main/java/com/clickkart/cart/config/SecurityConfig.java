// src/main/java/com/clickkart/cart/config/SecurityConfig.java
package com.clickkart.cart.config;

import com.clickkart.cart.constant.ApiPaths;
import com.clickkart.cart.jwt.JwtAuthenticationFilter;
import com.clickkart.cart.jwt.JwtService;
import com.clickkart.cart.security.AuthenticatedPrincipal;
import com.clickkart.cart.security.InternalApiKeyFilter;
import com.clickkart.cart.security.RestAccessDeniedHandler;
import com.clickkart.cart.security.RestAuthenticationEntryPoint;
import com.clickkart.cart.security.RevocationService;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * The smallest authorization surface on the platform: authenticated customers, plus one
 * shared-secret internal route.
 *
 * <p>There are no roles here at all - no {@code @PreAuthorize}, no SELLER or ADMIN branch. Every
 * signed-in account may have a cart, including a seller who wants to buy something, and no role
 * grants access to anyone else's. Adding a role check would not make anything safer; ownership is
 * what protects a cart, and that is enforced by deriving the owner from the verified token rather
 * than from anything the client sends.
 *
 * <p><strong>Nothing here is public.</strong> Like Order and Payment, and unlike Category, Product
 * and Inventory, this service has no anonymous surface - so the JWT filter gets no optional-auth list
 * and a missing token is always a clean 401 rather than an anonymous request refused later.
 */
@Configuration
@EnableConfigurationProperties(CartProperties.class)
@EnableMethodSecurity
public class SecurityConfig {

    private static final String SYSTEM_ACTOR = "system";

    private static final List<String> INFRA_PATHS = List.of(
            ApiPaths.ACTUATOR_HEALTH,
            ApiPaths.ACTUATOR_HEALTH_WILDCARD,
            ApiPaths.ACTUATOR_PROMETHEUS,
            ApiPaths.SWAGGER_UI,
            ApiPaths.SWAGGER_UI_WILDCARD,
            ApiPaths.API_DOCS_WILDCARD);

    private static final List<String> INTERNAL_PATHS = List.of(ApiPaths.INTERNAL_WILDCARD);

    /**
     * Paths {@link JwtAuthenticationFilter} must not attempt to authenticate. Deliberately a
     * different list from what is permitted: "the JWT filter skips this" and "anyone may call this"
     * are separate questions, and conflating them is how an internal endpoint quietly becomes an
     * anonymous one.
     */
    private static final List<String> JWT_EXEMPT_PATHS =
            Stream.concat(INFRA_PATHS.stream(), INTERNAL_PATHS.stream()).toList();

    private static final long HSTS_MAX_AGE_SECONDS = Duration.ofDays(365).toSeconds();

    private static final String CONTENT_SECURITY_POLICY = "default-src 'self'; frame-ancestors 'none'";

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            JwtService jwtService,
            RevocationService revocationService,
            HandlerExceptionResolver handlerExceptionResolver) {
        return new JwtAuthenticationFilter(jwtService, revocationService, handlerExceptionResolver, JWT_EXEMPT_PATHS);
    }

    @Bean
    public InternalApiKeyFilter internalApiKeyFilter(
            CartProperties cartProperties, HandlerExceptionResolver handlerExceptionResolver) {
        return new InternalApiKeyFilter(cartProperties.getInternalApiKey(), handlerExceptionResolver, INTERNAL_PATHS);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CartProperties cartProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(
                Arrays.stream(cartProperties.getAllowedOrigins().split(",")).map(String::trim).toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            InternalApiKeyFilter internalApiKeyFilter,
            CorsConfigurationSource corsConfigurationSource,
            RestAuthenticationEntryPoint restAuthenticationEntryPoint,
            RestAccessDeniedHandler restAccessDeniedHandler)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(INFRA_PATHS.toArray(new String[0]))
                        .permitAll()
                        .requestMatchers(INTERNAL_PATHS.toArray(new String[0]))
                        .hasRole("INTERNAL")
                        .anyRequest()
                        .authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(internalApiKeyFilter, JwtAuthenticationFilter.class)
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))
                .headers(headers -> headers
                        .frameOptions(frameOptions -> frameOptions.deny())
                        .contentTypeOptions(contentTypeOptions -> {})
                        .cacheControl(cacheControl -> {})
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(HSTS_MAX_AGE_SECONDS)));
        return http.build();
    }

    /**
     * createdBy/updatedBy source (Rule 3). Falls back to {@value #SYSTEM_ACTOR}, which here covers
     * the pruning job and Order Service emptying a cart after checkout - both real writers with no
     * customer principal attached.
     */
    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(authentication -> authentication.getPrincipal())
                .filter(AuthenticatedPrincipal.class::isInstance)
                .map(AuthenticatedPrincipal.class::cast)
                .map(AuthenticatedPrincipal::userId)
                .or(() -> Optional.of(SYSTEM_ACTOR));
    }
}
