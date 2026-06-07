package com.mythicrealm.api;

// Deprecated: use com.mythicrealm.common.api.ApiResponse instead
@Deprecated(forRemoval = true)
public record ApiResponse<T>(
    boolean success,
    T data,
    String message
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message);
    }
}
