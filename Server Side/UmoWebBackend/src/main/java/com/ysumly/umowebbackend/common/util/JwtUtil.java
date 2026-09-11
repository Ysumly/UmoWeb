package com.ysumly.umowebbackend.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

@Component
public class JwtUtil {

    private static final String TOKEN_VERSION_CLAIM = "ver";

    private final String secret;
    private final int expirationHours;

    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.expiration-hours:24}") int expirationHours) {
        this.secret = secret;
        this.expirationHours = expirationHours;
    }

    private SecretKey getKey() {
        // SHA-256 哈希确保密钥始终为 256 bit，无论原始 secret 长度
        byte[] keyBytes;
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            keyBytes = sha256.digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /** 签发 JWT token */
    public String generateToken(String username, int tokenVersion) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationHours * 3600_000L);

        return Jwts.builder()
                .subject(username)
                .claim(TOKEN_VERSION_CLAIM, tokenVersion)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getKey())
                .compact();
    }

    /** 解析 token 中的 username */
    public String parseUsername(String token) {
        return parseClaims(token).getSubject();
    }

    /** 解析 token 中的用户 tokenVersion。 */
    public int parseTokenVersion(String token) {
        Number value = parseClaims(token).get(TOKEN_VERSION_CLAIM, Number.class);
        if (value == null) {
            throw new IllegalArgumentException("Token version claim is missing");
        }
        return value.intValue();
    }

    /** 校验 token 是否有效 */
    public boolean validate(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
