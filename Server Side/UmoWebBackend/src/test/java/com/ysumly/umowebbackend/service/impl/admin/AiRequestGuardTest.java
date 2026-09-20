package com.ysumly.umowebbackend.service.impl.admin;

import com.ysumly.umowebbackend.common.exception.BusinessException;
import com.ysumly.umowebbackend.config.AiProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiRequestGuardTest {

    @Test
    void rejectsSecondConcurrentRequestWithoutQueueing() {
        AiRequestGuard guard = new AiRequestGuardImpl(properties());

        try (AiRequestGuard.Lease ignored = guard.acquire()) {
            assertThatThrownBy(guard::acquire)
                    .isInstanceOf(BusinessException.class)
                    .satisfies(error -> org.assertj.core.api.Assertions.assertThat(
                                    ((BusinessException) error).getCode())
                            .isEqualTo(429));
        }

        assertThatCode(guard::acquire).doesNotThrowAnyException();
    }

    @Test
    void releasesPermitWhenRequestFails() {
        AiRequestGuard guard = new AiRequestGuardImpl(properties());

        assertThatThrownBy(() -> {
            try (AiRequestGuard.Lease ignored = guard.acquire()) {
                throw new IllegalStateException("provider failed");
            }
        }).isInstanceOf(IllegalStateException.class);

        assertThatCode(guard::acquire).doesNotThrowAnyException();
    }

    @Test
    void allowsMoreThanFiveSequentialRequests() {
        AiRequestGuard guard = new AiRequestGuardImpl(properties());

        for (int i = 0; i < 6; i++) {
            try (AiRequestGuard.Lease ignored = guard.acquire()) {
                // Completed requests must not consume a time-window quota.
            }
        }
    }

    private AiProperties properties() {
        AiProperties properties = new AiProperties();
        properties.setMaxConcurrentRequests(1);
        return properties;
    }
}
