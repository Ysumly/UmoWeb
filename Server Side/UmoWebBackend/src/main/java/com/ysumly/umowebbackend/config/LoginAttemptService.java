package com.ysumly.umowebbackend.config;

import com.ysumly.umowebbackend.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class LoginAttemptService {

    private static final int CLEANUP_INTERVAL = 256;

    private final int maxFailures;
    private final long windowMillis;
    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final AtomicLong operations = new AtomicLong();

    public LoginAttemptService(@Value("${app.security.login-max-failures:5}") int maxFailures,
                               @Value("${app.security.login-window-seconds:900}") long windowSeconds) {
        this.maxFailures = maxFailures;
        this.windowMillis = windowSeconds * 1000L;
    }

    public void checkAllowed(String key) {
        Attempt attempt = attempts.get(key);
        if (attempt == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!attempt.expired(now, windowMillis) && attempt.failures >= maxFailures) {
            long waitSeconds = Math.max(1,
                    (windowMillis - (now - attempt.windowStartedAt) + 999) / 1000);
            throw new BusinessException(429,
                    "Too many login attempts. Please wait " + waitSeconds + " seconds.");
        }
    }

    public void recordFailure(String key) {
        long now = System.currentTimeMillis();
        attempts.compute(key, (ignored, existing) -> {
            if (existing == null || existing.expired(now, windowMillis)) {
                return new Attempt(now, 1);
            }
            existing.failures++;
            return existing;
        });
        cleanupIfNeeded(now);
    }

    public void recordSuccess(String key) {
        attempts.remove(key);
        cleanupIfNeeded(System.currentTimeMillis());
    }

    private void cleanupIfNeeded(long now) {
        if (operations.incrementAndGet() % CLEANUP_INTERVAL != 0) {
            return;
        }
        attempts.entrySet().removeIf(entry -> entry.getValue().expired(now, windowMillis));
    }

    private static final class Attempt {
        private final long windowStartedAt;
        private int failures;

        private Attempt(long windowStartedAt, int failures) {
            this.windowStartedAt = windowStartedAt;
            this.failures = failures;
        }

        private boolean expired(long now, long windowMillis) {
            return now - windowStartedAt >= windowMillis;
        }
    }
}
