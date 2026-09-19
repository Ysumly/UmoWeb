package com.ysumly.umowebbackend.service.impl.admin;

import com.ysumly.umowebbackend.common.exception.BusinessException;
import com.ysumly.umowebbackend.config.AiProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiRequestGuardTest {

    @Test
    void rejectsSecondConcurrentRequestWithoutQueueing() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-18T10:00:00Z"));
        AiRequestGuard guard = new AiRequestGuardImpl(properties(), clock);

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
        MutableClock clock = new MutableClock(Instant.parse("2026-09-18T10:00:00Z"));
        AiRequestGuard guard = new AiRequestGuardImpl(properties(), clock);

        assertThatThrownBy(() -> {
            try (AiRequestGuard.Lease ignored = guard.acquire()) {
                throw new IllegalStateException("provider failed");
            }
        }).isInstanceOf(IllegalStateException.class);

        assertThatCode(guard::acquire).doesNotThrowAnyException();
    }

    @Test
    void allowsFifthRequestAndRejectsSixthWithinWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-18T10:00:00Z"));
        AiRequestGuard guard = new AiRequestGuardImpl(properties(), clock);

        for (int i = 0; i < 5; i++) {
            try (AiRequestGuard.Lease ignored = guard.acquire()) {
                // Successful attempts consume the window quota.
            }
        }

        assertThatThrownBy(guard::acquire)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("429");
    }

    @Test
    void expiredRequestsLeaveTheWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-18T10:00:00Z"));
        AiRequestGuard guard = new AiRequestGuardImpl(properties(), clock);

        for (int i = 0; i < 5; i++) {
            try (AiRequestGuard.Lease ignored = guard.acquire()) {
                // Fill the current window.
            }
        }
        clock.advance(Duration.ofSeconds(601));

        assertThatCode(guard::acquire).doesNotThrowAnyException();
    }

    private AiProperties properties() {
        AiProperties properties = new AiProperties();
        properties.setMaxRequestsPerWindow(5);
        properties.setRateLimitWindowSeconds(600);
        properties.setMaxConcurrentRequests(1);
        return properties;
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
