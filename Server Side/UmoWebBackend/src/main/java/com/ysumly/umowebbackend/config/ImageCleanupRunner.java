package com.ysumly.umowebbackend.config;

import com.ysumly.umowebbackend.service.impl.admin.ImageCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
public class ImageCleanupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ImageCleanupRunner.class);

    private final ImageCleanupService cleanupService;

    public ImageCleanupRunner(ImageCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            cleanupService.retryPending();
        } catch (RuntimeException e) {
            log.error("Failed to retry pending image cleanup tasks", e);
        }
    }
}
