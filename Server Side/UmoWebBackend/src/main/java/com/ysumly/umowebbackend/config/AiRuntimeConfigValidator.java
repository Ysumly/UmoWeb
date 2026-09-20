package com.ysumly.umowebbackend.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class AiRuntimeConfigValidator {

    private final AiProperties properties;

    public AiRuntimeConfigValidator(AiProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void validate() {
        requirePositive(properties.getMaxInputChars(), "app.ai.max-input-chars");
        requirePositive(properties.getMaxOutputChars(), "app.ai.max-output-chars");
        requirePositive(properties.getTimeoutSeconds(), "app.ai.timeout-seconds");
        requirePositive(properties.getMaxConcurrentRequests(), "app.ai.max-concurrent-requests");

        if (!properties.isEnabled()) {
            return;
        }
        if (isBlank(properties.getDeepseek().getApiKey())) {
            throw new IllegalStateException("DEEPSEEK_API_KEY must be set when APP_AI_ENABLED=true");
        }
        if (isBlank(properties.getDeepseek().getModel())) {
            throw new IllegalStateException("DEEPSEEK_MODEL must be set when APP_AI_ENABLED=true");
        }
    }

    private void requirePositive(int value, String propertyName) {
        if (value <= 0) {
            throw new IllegalStateException(propertyName + " must be positive");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
