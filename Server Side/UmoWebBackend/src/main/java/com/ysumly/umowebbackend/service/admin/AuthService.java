package com.ysumly.umowebbackend.service.admin;

import com.ysumly.umowebbackend.model.dto.LoginRequest;
import java.util.Map;

public interface AuthService {
    Map<String, Object> login(String clientIp, LoginRequest request);
    void changePassword(String username, String oldPassword, String newPassword);
}
