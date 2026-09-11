package com.ysumly.umowebbackend.controller.admin;

import com.ysumly.umowebbackend.config.ClientIpResolver;
import com.ysumly.umowebbackend.model.dto.ChangePasswordRequest;
import com.ysumly.umowebbackend.model.dto.LoginRequest;
import com.ysumly.umowebbackend.service.admin.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AuthController {

    private final AuthService authService;
    private final ClientIpResolver clientIpResolver;

    public AuthController(AuthService authService, ClientIpResolver clientIpResolver) {
        this.authService = authService;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest request,
                                                      HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.login(clientIpResolver.resolve(httpRequest), request));
    }

    @PutMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                                HttpServletRequest httpRequest) {
        String username = (String) httpRequest.getAttribute("adminUsername");
        authService.changePassword(username, request.getOldPassword(), request.getNewPassword());
        return ResponseEntity.noContent().build();
    }
}
