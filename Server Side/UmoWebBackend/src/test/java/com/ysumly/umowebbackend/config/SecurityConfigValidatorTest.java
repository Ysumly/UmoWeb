package com.ysumly.umowebbackend.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigValidatorTest {

    @Test
    void rejectsDefaultCredentialsWhenOverridesAreDisabled() {
        SecurityConfigValidator validator = new SecurityConfigValidator(
                false,
                "change-me-in-production-this-is-a-default-only",
                "admin123");

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void allowsExplicitDevelopmentOverride() {
        SecurityConfigValidator validator = new SecurityConfigValidator(
                true,
                "change-me-in-production-this-is-a-default-only",
                "admin123");

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void acceptsNonDefaultCredentialsInProductionMode() {
        SecurityConfigValidator validator = new SecurityConfigValidator(
                false,
                "a-production-secret-value-that-is-not-default",
                "a-strong-admin-password");

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }
}
