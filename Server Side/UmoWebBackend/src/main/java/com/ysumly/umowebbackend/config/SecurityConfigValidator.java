package com.ysumly.umowebbackend.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SecurityConfigValidator {

    public static final String DEFAULT_JWT_SECRET = "change-me-in-production-this-is-a-default-only";
    public static final String DEFAULT_ADMIN_PASSWORD = "admin123";

    private static final Logger log = LoggerFactory.getLogger(SecurityConfigValidator.class);

    private final boolean allowDefaultCredentials;
    private final String jwtSecret;
    private final String adminPassword;

    public SecurityConfigValidator(
            @Value("${app.security.allow-default-credentials:false}") boolean allowDefaultCredentials,
            @Value("${app.jwt.secret}") String jwtSecret,
            @Value("${app.init.admin-password}") String adminPassword) {
        this.allowDefaultCredentials = allowDefaultCredentials;
        this.jwtSecret = jwtSecret;
        this.adminPassword = adminPassword;
    }

    @PostConstruct
    public void validate() {
        if (allowDefaultCredentials) {
            log.warn("允许使用默认开发凭据；生产环境请设置 SPRING_PROFILES_ACTIVE=prod 并覆盖 JWT_SECRET、INIT_ADMIN_PASS");
            return;
        }
        if (jwtSecret == null || jwtSecret.isBlank() || DEFAULT_JWT_SECRET.equals(jwtSecret)) {
            throw new IllegalStateException("JWT_SECRET must be set to a non-default value");
        }
        if (adminPassword == null || adminPassword.isBlank() || DEFAULT_ADMIN_PASSWORD.equals(adminPassword)) {
            throw new IllegalStateException("INIT_ADMIN_PASS must be set to a non-default value");
        }
    }
}
