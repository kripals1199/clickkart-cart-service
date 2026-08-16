// clickkart-cart-service/src/main/java/com/clickkart/cart/jwt/JwtAuthenticationFilter.java
package com.clickkart.cart.jwt;

import com.clickkart.cart.constant.LoggerNames;
import com.clickkart.cart.constant.MdcKeys;
import com.clickkart.cart.exception.DownstreamServiceUnavailableException;
import com.clickkart.cart.exception.MissingCorrelationIdException;
import com.clickkart.cart.security.AuthenticatedPrincipal;
import com.clickkart.cart.security.RevocationService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Independently authenticates every request from the {@code Authorization} bearer token.
 *
 * <p><strong>The Gateway's {@code X-User-Id} and {@code X-User-Roles} headers are deliberately
 * ignored.</strong> The Gateway sets them after its own validation, and its Javadoc describes them
 * as removing the need for downstream services to re-validate - but trusting them here would make
 * caller identity a client-supplied string. Anything able to open a socket to this service (another
 * pod, a misconfigured ingress, a port-forward, anyone inside the cluster network) could then send
 * {@code X-User-Id: <someone-else>} with no token at all and place orders in that customer's name,
 * against their address book, or read what they have bought. The Gateway is a cheap first filter,
 * not the authorization boundary - which is what {@code docs/architecture/ARCHITECTURE.md} requires
 * of every service, and what makes this service safe to reach directly. Identity comes from the
 * signature-verified token, or the request is rejected.
 *
 * <p><strong>No optional-auth list, unlike the Category, Product and Inventory copies.</strong>
 * Those services have a public catalog to browse; this one has nothing a stranger can be shown. A
 * missing token is therefore always a clean 401 here rather than an anonymous request that gets
 * refused later by an authorization rule, and the absent list is the thing to preserve - adding one
 * would mean some order route had become reachable without a token.
 *
 * <p>Three checks must all pass, in order: the signature and expiry ({@link JwtService}), the
 * revocation list (so logout takes effect immediately rather than at natural expiry), and the
 * presence of the {@code correlationId} claim minted by Auth Service at login (Rule 13 - this
 * service is a correlation-id receiver and never mints its own).
 */
@Slf4j(topic = LoggerNames.SECURITY)
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final RevocationService revocationService;
    private final HandlerExceptionResolver handlerExceptionResolver;
    /** Skipped entirely - actuator, docs, and the shared-secret internal surface. */
    private final List<String> publicPaths;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (matchesAny(request.getServletPath(), publicPaths)) {
            chain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            handlerExceptionResolver.resolveException(
                    request, response, null, new BadCredentialsException("Missing or malformed Authorization header"));
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        Claims claims;
        try {
            claims = jwtService.parseAndValidate(token);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT_REJECTED reason=invalid-or-expired path={} remoteAddr={} cause={}",
                    request.getRequestURI(), request.getRemoteAddr(), e.toString());
            handlerExceptionResolver.resolveException(
                    request, response, null, new BadCredentialsException("Invalid or expired token"));
            return;
        }

        String jti = claims.getId();
        boolean revoked;
        try {
            revoked = revocationService.isRevoked(jti);
        } catch (DataAccessException e) {
            // Redis is required, not best-effort: without it there is no way to honor a prior
            // logout, so every access token would become un-revocable until natural expiry. Fail
            // the request rather than silently treating it as "not revoked".
            log.warn("REVOCATION_CHECK_UNAVAILABLE jti={} path={} remoteAddr={} cause={}",
                    jti, request.getRequestURI(), request.getRemoteAddr(), e.toString());
            handlerExceptionResolver.resolveException(
                    request, response, null,
                    new DownstreamServiceUnavailableException("Token revocation check (Redis)", e));
            return;
        }

        if (revoked) {
            log.warn("JWT_REJECTED reason=revoked jti={} path={} remoteAddr={}",
                    jti, request.getRequestURI(), request.getRemoteAddr());
            handlerExceptionResolver.resolveException(
                    request, response, null, new BadCredentialsException("Token has been revoked"));
            return;
        }

        String correlationId = claims.get(JwtClaimNames.CORRELATION_ID, String.class);
        if (correlationId == null || correlationId.isBlank()) {
            handlerExceptionResolver.resolveException(
                    request, response, null,
                    new MissingCorrelationIdException("Token is missing the required correlationId claim"));
            return;
        }

        Set<String> roles = parseRoles(claims.get(JwtClaimNames.ROLES, String.class));
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                claims.getSubject(), roles, correlationId, jti, claims.getExpiration().toInstant());

        // Role names in the claim are already ROLE_-prefixed (e.g. ROLE_ADMIN, seeded by Auth
        // Service from RoleType), so hasRole('ADMIN') - which itself prepends "ROLE_" when
        // checking - matches correctly. Prefixing again here would produce "ROLE_ROLE_ADMIN" and
        // silently break every @PreAuthorize("hasRole(...)") check.
        var authorities = roles.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        MDC.put(MdcKeys.CORRELATION_ID, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MdcKeys.CORRELATION_ID);
            SecurityContextHolder.clearContext();
        }
    }

    private Set<String> parseRoles(String rolesClaim) {
        if (rolesClaim == null || rolesClaim.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(rolesClaim.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private boolean matchesAny(String path, List<String> patterns) {
        for (String pattern : patterns) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }
}
