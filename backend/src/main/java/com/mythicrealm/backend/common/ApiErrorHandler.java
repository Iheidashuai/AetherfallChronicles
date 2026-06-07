package com.mythicrealm.backend.common;

import java.time.Instant;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiErrorHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, Object>> handleApiException(ApiException error) {
        return ResponseEntity.status(error.status()).body(errorBody(error.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException error) {
        var message = error.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(fieldError -> fieldError.getField() + " " + fieldError.getDefaultMessage())
            .orElse("请求参数无效");
        return ResponseEntity.badRequest().body(errorBody(message));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<Map<String, Object>> handleDuplicate(DuplicateKeyException error) {
        return ResponseEntity.badRequest().body(errorBody("数据已存在或发生重复操作"));
    }

    private Map<String, Object> errorBody(String message) {
        return Map.of(
            "message", message,
            "timestamp", Instant.now().toString()
        );
    }
}
