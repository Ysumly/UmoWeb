package com.ysumly.umowebbackend.config;

import com.ysumly.umowebbackend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptServiceTest {

    @Test
    void blocksAfterConfiguredFailureCount() {
        LoginAttemptService service = new LoginAttemptService(3, 60);

        service.recordFailure("admin|203.0.113.10");
        service.recordFailure("admin|203.0.113.10");
        service.recordFailure("admin|203.0.113.10");

        assertThatThrownBy(() -> service.checkAllowed("admin|203.0.113.10"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(429);
    }

    @Test
    void successfulLoginClearsFailures() {
        LoginAttemptService service = new LoginAttemptService(3, 60);
        service.recordFailure("admin|203.0.113.10");
        service.recordFailure("admin|203.0.113.10");

        service.recordSuccess("admin|203.0.113.10");
        service.checkAllowed("admin|203.0.113.10");
    }
}
