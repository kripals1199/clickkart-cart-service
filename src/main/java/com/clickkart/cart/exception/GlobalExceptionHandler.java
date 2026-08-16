// clickkart-cart-service/src/main/java/com/clickkart/cart/exception/GlobalExceptionHandler.java
package com.clickkart.cart.exception;

import com.clickkart.cart.constant.LoggerNames;
import com.clickkart.cart.constant.MdcKeys;
import com.clickkart.cart.dto.ApiResponse;
import com.clickkart.cart.dto.ErrorDetail;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Central mapping to the standard {@link ApiResponse} envelope (Rule 12) - own copy of the pattern
 * established in Auth Service (Rule 4).
 *
 * <p>Stack traces go through the logger or nowhere.


 */
@Slf4j(topic = LoggerNames.SECURITY)
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String DEFAULT_FIELD_ERROR_MESSAGE = "invalid value";

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(
            BadCredentialsException ex, HttpServletRequest request) {
        return respond(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED, ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("ACCESS_DENIED path={} correlationId={}", request.getRequestURI(), MDC.get(MdcKeys.CORRELATION_ID));
        return respond(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED,
                "You do not have permission to perform this action", request);
    }

    @ExceptionHandler(MissingCorrelationIdException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingCorrelationId(
            MissingCorrelationIdException ex, HttpServletRequest request) {
        return respond(HttpStatus.UNAUTHORIZED, ErrorCode.MISSING_CORRELATION_ID, ex.getMessage(), request);
    }

    /** A SKU the caller named is not in their cart. Their own cart, always - see the controller. */
    @ExceptionHandler(CartItemNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleItemNotFound(
            CartItemNotFoundException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, ErrorCode.CART_ITEM_NOT_FOUND, ex.getMessage(), request);
    }

    /** 409 rather than 404: the SKU may well exist, the catalog just will not sell it right now. */
    @ExceptionHandler(SkuNotAddableException.class)
    public ResponseEntity<ApiResponse<Void>> handleSkuNotAddable(
            SkuNotAddableException ex, HttpServletRequest request) {
        ErrorDetail detail = ErrorDetail.withMetadata(ErrorCode.SKU_NOT_ADDABLE, Map.of("sku", ex.getSku()));
        return respond(HttpStatus.CONFLICT, detail, ex.getMessage(), request);
    }

    @ExceptionHandler(CartTooLargeException.class)
    public ResponseEntity<ApiResponse<Void>> handleCartTooLarge(
            CartTooLargeException ex, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, ErrorCode.CART_TOO_LARGE, ex.getMessage(), request);
    }


    /**
     * Two actors writing the same order at once - a cancellation racing a payment result, or a
     * seller racing the expiry sweeper. Retryable, so 409 rather than 500.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(
            OptimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("CONCURRENT_MODIFICATION path={} correlationId={} cause={}",
                request.getRequestURI(), MDC.get(MdcKeys.CORRELATION_ID), ex.toString());
        return respond(HttpStatus.CONFLICT, ErrorCode.CONCURRENT_MODIFICATION,
                "Your cart was changed by another request - please retry", request);
    }

    @ExceptionHandler(DownstreamServiceUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleDownstreamUnavailable(
            DownstreamServiceUnavailableException ex, HttpServletRequest request) {
        log.error("DOWNSTREAM_UNAVAILABLE service={} path={} correlationId={} cause={}",
                ex.getServiceName(), request.getRequestURI(), MDC.get(MdcKeys.CORRELATION_ID), ex.toString());
        return respond(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.SERVICE_UNAVAILABLE, ex.getMessage(), request);
    }

    /** Conditional rules the annotations cannot express, e.g. the same SKU appearing on two lines. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fieldError -> fieldErrors.put(
                fieldError.getField(),
                fieldError.getDefaultMessage() == null ? DEFAULT_FIELD_ERROR_MESSAGE : fieldError.getDefaultMessage()));
        ErrorDetail errorDetail = ErrorDetail.withFieldErrors(ErrorCode.VALIDATION_FAILED, fieldErrors);
        return respond(HttpStatus.BAD_REQUEST, errorDetail, "One or more fields failed validation", request);
    }

    /**
     * A required header that was not sent - in practice always {@code Idempotency-Key} on checkout.
     *
     * <p>Without this handler Spring's {@code MissingRequestHeaderException} falls through to the
     * catch-all below and the client gets a 500, which says "we broke" about a request the client
     * can fix. It matters more here than it would elsewhere: the header exists precisely so a
     * nervous client retries safely, and a 500 is the response most likely to make one retry - so
     * the mislabelled error would encourage exactly the behaviour the header exists to make safe.
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(
            MissingRequestHeaderException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        fieldErrors.put(ex.getHeaderName(), "required header is missing");
        ErrorDetail errorDetail = ErrorDetail.withFieldErrors(ErrorCode.VALIDATION_FAILED, fieldErrors);
        return respond(HttpStatus.BAD_REQUEST, errorDetail,
                "Required header '" + ex.getHeaderName() + "' is missing", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        fieldErrors.put(ex.getName(), DEFAULT_FIELD_ERROR_MESSAGE);
        ErrorDetail errorDetail = ErrorDetail.withFieldErrors(ErrorCode.VALIDATION_FAILED, fieldErrors);
        return respond(HttpStatus.BAD_REQUEST, errorDetail, "One or more fields failed validation", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMalformedRequestBody(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                "Request body is missing or malformed", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred", request);
    }

    private ResponseEntity<ApiResponse<Void>> respond(
            HttpStatus status, String code, String message, HttpServletRequest request) {
        return respond(status, ErrorDetail.of(code), message, request);
    }

    private ResponseEntity<ApiResponse<Void>> respond(
            HttpStatus status, ErrorDetail errorDetail, String message, HttpServletRequest request) {
        String correlationId = MDC.get(MdcKeys.CORRELATION_ID);
        ApiResponse<Void> body =
                ApiResponse.error(status.value(), errorDetail, message, request.getRequestURI(), correlationId);
        return ResponseEntity.status(status).body(body);
    }

    /** Stable, machine-readable codes a caller can switch on - never the free-text message. */
    private static final class ErrorCode {
        private ErrorCode() {}

        static final String UNAUTHENTICATED = "UNAUTHENTICATED";
        static final String ACCESS_DENIED = "ACCESS_DENIED";
        static final String MISSING_CORRELATION_ID = "MISSING_CORRELATION_ID";
        static final String CART_ITEM_NOT_FOUND = "CART_ITEM_NOT_FOUND";
        static final String SKU_NOT_ADDABLE = "SKU_NOT_ADDABLE";
        static final String CART_TOO_LARGE = "CART_TOO_LARGE";
        static final String CONCURRENT_MODIFICATION = "CONCURRENT_MODIFICATION";
        static final String SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";
        static final String VALIDATION_FAILED = "VALIDATION_FAILED";
        static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    }
}
