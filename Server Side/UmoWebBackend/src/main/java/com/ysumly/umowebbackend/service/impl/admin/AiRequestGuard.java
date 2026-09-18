package com.ysumly.umowebbackend.service.impl.admin;

public interface AiRequestGuard {

    Lease acquire();

    interface Lease extends AutoCloseable {

        @Override
        void close();
    }
}
