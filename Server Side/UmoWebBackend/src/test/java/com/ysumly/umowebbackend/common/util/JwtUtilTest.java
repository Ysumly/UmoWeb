package com.ysumly.umowebbackend.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    @Test
    void tokenCarriesTokenVersionAndUsername() {
        JwtUtil jwtUtil = new JwtUtil("a-test-secret-that-is-not-production", 24);

        String token = jwtUtil.generateToken("admin", 7);

        assertThat(jwtUtil.validate(token)).isTrue();
        assertThat(jwtUtil.parseUsername(token)).isEqualTo("admin");
        assertThat(jwtUtil.parseTokenVersion(token)).isEqualTo(7);
    }

    @Test
    void expiredTokenIsRejected() {
        JwtUtil jwtUtil = new JwtUtil("a-test-secret-that-is-not-production", -1);

        String token = jwtUtil.generateToken("admin", 1);

        assertThat(jwtUtil.validate(token)).isFalse();
    }
}
