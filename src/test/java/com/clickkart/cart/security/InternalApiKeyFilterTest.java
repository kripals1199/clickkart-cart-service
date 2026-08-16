// clickkart-cart-service/src/test/java/com/clickkart/cart/security/InternalApiKeyFilterTest.java
package com.clickkart.cart.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.clickkart.cart.exception.MissingCorrelationIdException;
import jakarta.servlet.FilterChain;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * The internal surface can resolve any user's address by id - the capability the customer-facing
 * API deliberately refuses to expose - so the gate on it is worth pinning precisely.
 */
@ExtendWith(MockitoExtension.class)
class InternalApiKeyFilterTest {

    private static final String KEY = "s3cret-internal-key";

    @Mock private HandlerExceptionResolver handlerExceptionResolver;
    @Mock private FilterChain filterChain;

    private InternalApiKeyFilter filter(String configuredKey) {
        return new InternalApiKeyFilter(configuredKey, handlerExceptionResolver, List.of("/internal/**"));
    }

    private MockHttpServletRequest internalRequest() {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/internal/v1/carts/USR-1");
        request.setServletPath("/internal/v1/carts/USR-1");
        return request;
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aCorrectKeyAuthenticatesWithTheInternalRoleOnly() throws Exception {
        MockHttpServletRequest request = internalRequest();
        request.addHeader(InternalApiKeyFilter.API_KEY_HEADER, KEY);
        request.addHeader(InternalApiKeyFilter.CORRELATION_ID_HEADER, "corr-1");

        AtomicReference<Authentication> seen = new AtomicReference<>();
        doAnswer(invocation -> {
            seen.set(SecurityContextHolder.getContext().getAuthentication());
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter(KEY).doFilterInternal(request, new MockHttpServletResponse(), filterChain);

        assertThat(seen.get()).isNotNull();
        assertThat(seen.get().getAuthorities()).extracting(Object::toString).containsExactly("ROLE_INTERNAL");
        // Never a customer identity - an internal caller acts as a service, not as a user.
        assertThat(seen.get().getPrincipal()).isEqualTo("internal-service");
    }

    @Test
    void aWrongKeyIsRejected() throws Exception {
        MockHttpServletRequest request = internalRequest();
        request.addHeader(InternalApiKeyFilter.API_KEY_HEADER, "wrong-key");
        request.addHeader(InternalApiKeyFilter.CORRELATION_ID_HEADER, "corr-1");

        filter(KEY).doFilterInternal(request, new MockHttpServletResponse(), filterChain);

        verify(handlerExceptionResolver).resolveException(any(), any(), isNull(), any(BadCredentialsException.class));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void aMissingKeyIsRejected() throws Exception {
        MockHttpServletRequest request = internalRequest();
        request.addHeader(InternalApiKeyFilter.CORRELATION_ID_HEADER, "corr-1");

        filter(KEY).doFilterInternal(request, new MockHttpServletResponse(), filterChain);

        verify(handlerExceptionResolver).resolveException(any(), any(), isNull(), any(BadCredentialsException.class));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void anUnconfiguredKeyRefusesEveryoneRatherThanMatchingAnEmptyHeader() throws Exception {
        // The dangerous case: a blank expected key must not equal a blank presented key, which
        // would open the entire internal surface to any caller that sends the header empty.
        MockHttpServletRequest request = internalRequest();
        request.addHeader(InternalApiKeyFilter.API_KEY_HEADER, "");
        request.addHeader(InternalApiKeyFilter.CORRELATION_ID_HEADER, "corr-1");

        filter("").doFilterInternal(request, new MockHttpServletResponse(), filterChain);

        verify(handlerExceptionResolver).resolveException(any(), any(), isNull(), any(BadCredentialsException.class));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void aNullConfiguredKeyAlsoRefusesEveryone() throws Exception {
        MockHttpServletRequest request = internalRequest();
        request.addHeader(InternalApiKeyFilter.API_KEY_HEADER, "anything");
        request.addHeader(InternalApiKeyFilter.CORRELATION_ID_HEADER, "corr-1");

        filter(null).doFilterInternal(request, new MockHttpServletResponse(), filterChain);

        verify(handlerExceptionResolver).resolveException(any(), any(), isNull(), any(BadCredentialsException.class));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void aValidKeyWithoutACorrelationIdIsRejected() throws Exception {
        // Rule 13: internal callers propagate the id Auth Service minted, so a checkout stays
        // traceable across every service that touched it.
        MockHttpServletRequest request = internalRequest();
        request.addHeader(InternalApiKeyFilter.API_KEY_HEADER, KEY);

        filter(KEY).doFilterInternal(request, new MockHttpServletResponse(), filterChain);

        verify(handlerExceptionResolver)
                .resolveException(any(), any(), isNull(), any(MissingCorrelationIdException.class));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void nonInternalPathsPassThroughUntouched() throws Exception {
        // The customer-facing API must keep being authenticated by the JWT filter, not this one.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/cart");
        request.setServletPath("/api/v1/cart");

        filter(KEY).doFilterInternal(request, new MockHttpServletResponse(), filterChain);

        verify(filterChain).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
