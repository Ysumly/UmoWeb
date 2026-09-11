package com.ysumly.umowebbackend.config;

import com.ysumly.umowebbackend.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final ConcurrentHashMap<String, Long> lastRequestTime = new ConcurrentHashMap<>();
    private final ClientIpResolver clientIpResolver;
    private final long windowMillis;
    private final AtomicLong operations = new AtomicLong();
    private static final int CLEANUP_INTERVAL = 256;

    public RateLimitInterceptor(ClientIpResolver clientIpResolver,
                                @org.springframework.beans.factory.annotation.Value(
                                        "${app.security.search-rate-limit-seconds:10}") long windowSeconds) {
        this.clientIpResolver = clientIpResolver;
        this.windowMillis = windowSeconds * 1000L;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {

        String ip = clientIpResolver.resolve(request);
        long now = System.currentTimeMillis();
        AtomicBoolean blocked = new AtomicBoolean(false);
        AtomicLong waitSeconds = new AtomicLong();
        lastRequestTime.compute(ip, (ignored, lastTime) -> {
            if (lastTime != null && (now - lastTime) < windowMillis) {
                blocked.set(true);
                waitSeconds.set(Math.max(1,
                        (windowMillis - (now - lastTime) + 999) / 1000));
                return lastTime;
            }
            return now;
        });
        if (blocked.get()) {
            throw new BusinessException(429,
                    "Too many requests. Please wait " + waitSeconds.get() + " seconds.");
        }

        cleanupIfNeeded(now);
        return true;
    }

    private void cleanupIfNeeded(long now) {
        if (operations.incrementAndGet() % CLEANUP_INTERVAL != 0) {
            return;
        }
        lastRequestTime.entrySet().removeIf(entry -> now - entry.getValue() >= windowMillis);
    }
}
