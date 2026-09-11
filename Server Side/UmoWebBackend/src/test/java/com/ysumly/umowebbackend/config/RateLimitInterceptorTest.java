package com.ysumly.umowebbackend.config;

import com.ysumly.umowebbackend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitInterceptorTest {

    @Test
    void spoofedForwardedForCannotBypassSearchLimit() {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(
                new ClientIpResolver(Set.of()), 10);
        MockHttpServletRequest first = searchRequest("198.51.100.1");
        MockHttpServletRequest second = searchRequest("198.51.100.2");

        assertThat(interceptor.preHandle(first, new MockHttpServletResponse(), new Object()))
                .isTrue();
        assertThatThrownBy(() -> interceptor.preHandle(
                second, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(429);
    }

    private MockHttpServletRequest searchRequest(String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("X-Forwarded-For", forwardedFor);
        return request;
    }
}
