package com.mythicrealm.api.exception;

import com.mythicrealm.common.exception.BusinessException;
import com.mythicrealm.common.exception.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<Map<String, Object>> handleDomainException(DomainException e) {
        return ResponseEntity.badRequest().body(Map.of(
            "error", "DOMAIN_ERROR",
            "message", e.getMessage(),
            "timestamp", Instant.now()
        ));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessException(BusinessException e) {
        return ResponseEntity.badRequest().body(Map.of(
            "error", e.getCode(),
            "message", e.getMessage(),
            "timestamp", Instant.now()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
            "error", "INTERNAL_ERROR",
            "message", e.getMessage(),
            "timestamp", Instant.now()
        ));
    }
}
