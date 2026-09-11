package com.ysumly.umowebbackend.service.impl.admin;

import com.ysumly.umowebbackend.common.exception.UnauthorizedException;
import com.ysumly.umowebbackend.common.exception.BusinessException;
import com.ysumly.umowebbackend.common.util.JwtUtil;
import com.ysumly.umowebbackend.config.AdminInterceptor;
import com.ysumly.umowebbackend.config.LoginAttemptService;
import com.ysumly.umowebbackend.mapper.UserMapper;
import com.ysumly.umowebbackend.model.dto.LoginRequest;
import com.ysumly.umowebbackend.model.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AuthServiceImplTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtUtil jwtUtil = new JwtUtil("a-test-secret-that-is-not-production", 24);
    private final LoginAttemptService loginAttemptService = new LoginAttemptService(5, 60);
    private final AuthServiceImpl service = new AuthServiceImpl(
            userMapper, jwtUtil, passwordEncoder, loginAttemptService);

    @Test
    void normalLoginReturnsVersionedToken() {
        User user = user(1);
        when(userMapper.findByUsername("admin")).thenReturn(user);
        when(passwordEncoder.matches("correct", "hash")).thenReturn(true);
        LoginRequest request = loginRequest("admin", "correct");

        var result = service.login("203.0.113.10", request);

        String token = (String) result.get("token");
        assertThat(jwtUtil.validate(token)).isTrue();
        assertThat(jwtUtil.parseTokenVersion(token)).isEqualTo(1);
        assertThat(result).containsKey("expiresAt");
    }

    @Test
    void changingPasswordInvalidatesOldToken() {
        User user = user(1);
        when(userMapper.findByUsername("admin")).thenReturn(user);
        when(passwordEncoder.matches("old-password", "hash")).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
        doAnswer(invocation -> {
            user.setTokenVersion(user.getTokenVersion() + 1);
            return null;
        }).when(userMapper).updatePassword("admin", "new-hash");
        String oldToken = jwtUtil.generateToken("admin", user.getTokenVersion());

        service.changePassword("admin", "old-password", "new-password");

        AdminInterceptor interceptor = new AdminInterceptor(jwtUtil, userMapper);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + oldToken);
        assertThatThrownBy(() -> interceptor.preHandle(
                request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void repeatedLoginFailuresAreRateLimited() {
        User user = user(1);
        when(userMapper.findByUsername("admin")).thenReturn(user);
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);
        LoginRequest request = loginRequest("admin", "wrong");

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.login("203.0.113.10", request))
                    .isInstanceOf(UnauthorizedException.class);
        }

        assertThatThrownBy(() -> service.login("203.0.113.10", request))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(429);
    }

    @Test
    void loginFailureKeyCannotBeBypassedWithCaseOrWhitespace() {
        User user = user(1);
        when(userMapper.findByUsername("admin")).thenReturn(user);
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> service.login(
                    "203.0.113.10", loginRequest("admin", "wrong")))
                    .isInstanceOf(UnauthorizedException.class);
        }
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.login(
                    "203.0.113.10", loginRequest(" ADMIN ", "wrong")))
                    .isInstanceOf(UnauthorizedException.class);
        }

        assertThatThrownBy(() -> service.login(
                "203.0.113.10", loginRequest("admin", "wrong")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(429);
    }

    private User user(int tokenVersion) {
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        user.setPasswordHash("hash");
        user.setTokenVersion(tokenVersion);
        return user;
    }

    private LoginRequest loginRequest(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }
}
