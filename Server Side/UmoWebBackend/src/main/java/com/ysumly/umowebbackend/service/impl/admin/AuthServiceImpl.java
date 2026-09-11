package com.ysumly.umowebbackend.service.impl.admin;

import com.ysumly.umowebbackend.common.exception.UnauthorizedException;
import com.ysumly.umowebbackend.common.util.JwtUtil;
import com.ysumly.umowebbackend.config.LoginAttemptService;
import com.ysumly.umowebbackend.mapper.UserMapper;
import com.ysumly.umowebbackend.model.dto.LoginRequest;
import com.ysumly.umowebbackend.model.entity.User;
import com.ysumly.umowebbackend.service.admin.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;

    @Value("${app.jwt.expiration-hours:24}")
    private int expirationHours;

    public AuthServiceImpl(UserMapper userMapper, JwtUtil jwtUtil,
                           PasswordEncoder passwordEncoder,
                           LoginAttemptService loginAttemptService) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    public Map<String, Object> login(String clientIp, LoginRequest request) {
        String attemptKey = request.getUsername() + "|" + clientIp;
        loginAttemptService.checkAllowed(attemptKey);
        User user = userMapper.findByUsername(request.getUsername());
        if (user == null) {
            loginAttemptService.recordFailure(attemptKey);
            throw new UnauthorizedException("Invalid username or password");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            loginAttemptService.recordFailure(attemptKey);
            throw new UnauthorizedException("Invalid username or password");
        }
        loginAttemptService.recordSuccess(attemptKey);

        int tokenVersion = user.getTokenVersion() != null ? user.getTokenVersion() : 1;
        String token = jwtUtil.generateToken(user.getUsername(), tokenVersion);
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(expirationHours);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", token);
        result.put("expiresAt", expiresAt);
        return result;
    }

    @Override
    public void changePassword(String username, String oldPassword, String newPassword) {
        User user = userMapper.findByUsername(username);
        if (user == null) {
            throw new UnauthorizedException("用户不存在");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new UnauthorizedException("旧密码错误");
        }
        userMapper.updatePassword(username, passwordEncoder.encode(newPassword));
    }
}
