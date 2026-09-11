package com.ysumly.umowebbackend.config;

import com.ysumly.umowebbackend.common.exception.UnauthorizedException;
import com.ysumly.umowebbackend.common.util.JwtUtil;
import com.ysumly.umowebbackend.mapper.UserMapper;
import com.ysumly.umowebbackend.model.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminInterceptorTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final JwtUtil jwtUtil = new JwtUtil("a-test-secret-that-is-not-production", 24);
    private final AdminInterceptor interceptor = new AdminInterceptor(jwtUtil, userMapper);

    @Test
    void rejectsTokenWhoseVersionIsStale() {
        User user = new User();
        user.setUsername("admin");
        user.setTokenVersion(2);
        when(userMapper.findByUsername("admin")).thenReturn(user);
        String oldToken = jwtUtil.generateToken("admin", 1);
        MockHttpServletRequest request = requestWithToken(oldToken);

        assertThatThrownBy(() -> interceptor.preHandle(
                request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void acceptsTokenWhoseVersionMatchesUser() {
        User user = new User();
        user.setUsername("admin");
        user.setTokenVersion(2);
        when(userMapper.findByUsername("admin")).thenReturn(user);
        String token = jwtUtil.generateToken("admin", 2);
        MockHttpServletRequest request = requestWithToken(token);

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isTrue();
        assertThat(request.getAttribute("adminUsername")).isEqualTo("admin");
    }

    private MockHttpServletRequest requestWithToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
