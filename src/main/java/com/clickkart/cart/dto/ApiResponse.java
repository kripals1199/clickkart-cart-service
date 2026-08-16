// clickkart-cart-service/src/main/java/com/clickkart/cart/dto/ApiResponse.java
package com.clickkart.cart.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.Getter;

import java.time.Instant;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "timestamp", "status", "success", "data", "error", "message", "path", "correlationId" })
public final class ApiResponse<T> {

	private final String timestamp;
	private final int status;
	private final boolean success;
	private final T data;
	private final Object error;
	private final String message;
	private final String path;
	private final String correlationId;

	private ApiResponse(int status, boolean success, T data, Object error, String message, String path,
			String correlationId) {
		this.timestamp = Instant.now().toString();
		this.status = status;
		this.success = success;
		this.data = data;
		this.error = error;
		this.message = message;
		this.path = path;
		this.correlationId = correlationId;
	}

	public static <T> ApiResponse<T> success(T data, String path, String correlationId) {
		return new ApiResponse<>(200, true, data, null, null, path, correlationId);
	}

	public static <T> ApiResponse<T> success(int status, T data, String path, String correlationId) {
		return new ApiResponse<>(status, true, data, null, null, path, correlationId);
	}

	public static <T> ApiResponse<T> error(int status, Object error, String message, String path,
			String correlationId) {
		return new ApiResponse<>(status, false, null, error, message, path, correlationId);
	}

}
