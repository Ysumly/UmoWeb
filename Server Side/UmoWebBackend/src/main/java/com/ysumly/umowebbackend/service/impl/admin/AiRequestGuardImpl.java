package com.ysumly.umowebbackend.service.impl.admin;

import com.ysumly.umowebbackend.common.exception.BusinessException;
import com.ysumly.umowebbackend.config.AiProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class AiRequestGuardImpl implements AiRequestGuard {

    private final int maxRequestsPerWindow;
    private final Duration rateLimitWindow;
    private final Clock clock;
    private final Semaphore concurrency;
    private final Deque<Instant> acceptedRequests = new ArrayDeque<>();

    public AiRequestGuardImpl(AiProperties properties, Clock clock) {
        this.maxRequestsPerWindow = properties.getMaxRequestsPerWindow();
        this.rateLimitWindow = Duration.ofSeconds(properties.getRateLimitWindowSeconds());
        this.clock = clock;
        this.concurrency = new Semaphore(
                properties.getMaxConcurrentRequests(),
                true);
    }

    @Override
    public synchronized Lease acquire() {
        pruneExpired(clock.instant());
        if (acceptedRequests.size() >= maxRequestsPerWindow) {
            throw rateLimited();
        }
        if (!concurrency.tryAcquire()) {
            throw rateLimited();
        }
        acceptedRequests.addLast(clock.instant());
        return new LeaseImpl(concurrency);
    }

    private void pruneExpired(Instant now) {
        Instant cutoff = now.minus(rateLimitWindow);
        while (!acceptedRequests.isEmpty()
                && !acceptedRequests.peekFirst().isAfter(cutoff)) {
            acceptedRequests.removeFirst();
        }
    }

    private BusinessException rateLimited() {
        return new BusinessException(429, "429 AI 请求过于频繁，请稍后再试");
    }

    private static final class LeaseImpl implements Lease {

        private final Semaphore concurrency;
        private final AtomicBoolean closed = new AtomicBoolean();

        private LeaseImpl(Semaphore concurrency) {
            this.concurrency = concurrency;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                concurrency.release();
            }
        }
    }
}
