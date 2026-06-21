package com.mythicrealm.api.exception;

import com.mythicrealm.common.exception.BusinessException;
import com.mythicrealm.common.exception.DomainException;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<Map<String, Object>> handleDomainException(DomainException e) {
        return ResponseEntity.badRequest().body(errorBody("DOMAIN_ERROR", e.getMessage()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessException(BusinessException e) {
        return ResponseEntity.badRequest().body(errorBody(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler({ AsyncRequestNotUsableException.class, IOException.class })
    public void handleDisconnectedClient(Exception e) {
        // SSE clients can disconnect while the server is writing a heartbeat.
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody("INTERNAL_ERROR", e.getMessage()));
    }

    private Map<String, Object> errorBody(String error, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", error == null || error.isBlank() ? "ERROR" : error);
        body.put("message", message == null || message.isBlank() ? "请求处理失败" : message);
        body.put("timestamp", Instant.now());
        return body;
    }
}
