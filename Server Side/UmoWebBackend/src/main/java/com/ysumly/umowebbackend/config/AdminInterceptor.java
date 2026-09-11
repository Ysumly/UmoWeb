package com.ysumly.umowebbackend.config;

import com.ysumly.umowebbackend.common.exception.UnauthorizedException;
import com.ysumly.umowebbackend.common.util.JwtUtil;
import com.ysumly.umowebbackend.mapper.UserMapper;
import com.ysumly.umowebbackend.model.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;

    public AdminInterceptor(JwtUtil jwtUtil, UserMapper userMapper) {
        this.jwtUtil = jwtUtil;
        this.userMapper = userMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new UnauthorizedException("Missing or invalid Authorization header");
        }

        String token = header.substring(7);
        if (!jwtUtil.validate(token)) {
            throw new UnauthorizedException("Invalid or expired token");
        }

        String username;
        int tokenVersion;
        try {
            username = jwtUtil.parseUsername(token);
            tokenVersion = jwtUtil.parseTokenVersion(token);
        } catch (RuntimeException e) {
            throw new UnauthorizedException("Invalid or expired token");
        }

        User user = userMapper.findByUsername(username);
        if (user == null || user.getTokenVersion() == null
                || user.getTokenVersion() != tokenVersion) {
            throw new UnauthorizedException("Invalid or expired token");
        }

        request.setAttribute("adminUsername", username);
        return true;
    }
}
