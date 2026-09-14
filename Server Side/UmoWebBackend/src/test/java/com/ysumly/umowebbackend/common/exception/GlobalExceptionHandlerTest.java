package com.ysumly.umowebbackend.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void missingStaticResourceReturnsNotFoundInsteadOfInternalError() {
        NoResourceFoundException exception = new NoResourceFoundException(
                HttpMethod.GET,
                "No static resource",
                "/images/2026/09/deleted.png");

        ResponseEntity<Map<String, Object>> response =
                handler.handleMissingResource(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody())
                .containsEntry("code", 404)
                .containsEntry("message", "Resource not found: /images/2026/09/deleted.png");
    }
}
