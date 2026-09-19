package com.ysumly.umowebbackend.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiRuntimeConfigValidatorTest {

    @Test
    void disabledConfigurationDoesNotRequireCredentials() {
        AiProperties properties = properties();
        properties.setEnabled(false);

        assertThatCode(() -> new AiRuntimeConfigValidator(properties).validate())
                .doesNotThrowAnyException();
    }

    @Test
    void enabledConfigurationRequiresApiKey() {
        AiProperties properties = properties();
        properties.setEnabled(true);
        properties.getDeepseek().setModel("test-model");

        assertThatThrownBy(() -> new AiRuntimeConfigValidator(properties).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEEPSEEK_API_KEY");
    }

    @Test
    void enabledConfigurationRequiresModel() {
        AiProperties properties = properties();
        properties.setEnabled(true);
        properties.getDeepseek().setApiKey("secret-value");

        assertThatThrownBy(() -> new AiRuntimeConfigValidator(properties).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEEPSEEK_MODEL")
                .hasMessageNotContaining("secret-value");
    }

    @Test
    void nonPositiveRuntimeLimitsAreRejected() {
        AiProperties properties = properties();
        properties.setMaxInputChars(0);

        assertThatThrownBy(() -> new AiRuntimeConfigValidator(properties).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.ai.max-input-chars");
    }

    @Test
    void enabledConfigurationAcceptsCompleteCredentials() {
        AiProperties properties = properties();
        properties.setEnabled(true);
        properties.getDeepseek().setApiKey("secret-value");
        properties.getDeepseek().setModel("test-model");

        assertThatCode(() -> new AiRuntimeConfigValidator(properties).validate())
                .doesNotThrowAnyException();
    }

    private AiProperties properties() {
        AiProperties properties = new AiProperties();
        properties.setMaxInputChars(20_000);
        properties.setMaxOutputChars(60_000);
        properties.setTimeoutSeconds(180);
        properties.setMaxRequestsPerWindow(5);
        properties.setRateLimitWindowSeconds(600);
        properties.setMaxConcurrentRequests(1);
        return properties;
    }
}
