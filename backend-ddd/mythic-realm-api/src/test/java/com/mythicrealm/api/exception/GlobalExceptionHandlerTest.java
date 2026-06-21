package com.mythicrealm.api.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class GlobalExceptionHandlerTest {

    @Test
    void genericExceptionWithoutMessageStillProducesErrorBody() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        Map<String, Object> body = handler.handleException(new RuntimeException()).getBody();

        assertThat(body).isNotNull();
        assertThat(body).containsEntry("error", "INTERNAL_ERROR");
        assertThat(body).containsEntry("message", "请求处理失败");
        assertThat(body).containsKey("timestamp");
    }
}
