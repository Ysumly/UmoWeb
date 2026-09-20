package com.ysumly.umowebbackend.service.impl.admin;

import com.ysumly.umowebbackend.common.exception.BusinessException;
import com.ysumly.umowebbackend.config.AiProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class AiRequestGuardImpl implements AiRequestGuard {

    private final Semaphore concurrency;

    public AiRequestGuardImpl(AiProperties properties) {
        this.concurrency = new Semaphore(
                properties.getMaxConcurrentRequests(),
                true);
    }

    @Override
    public Lease acquire() {
        if (!concurrency.tryAcquire()) {
            throw rateLimited();
        }
        return new LeaseImpl(concurrency);
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
