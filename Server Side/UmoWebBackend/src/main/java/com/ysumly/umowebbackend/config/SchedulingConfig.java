package com.ysumly.umowebbackend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@ConditionalOnProperty(
        prefix = "app.scheduling",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableScheduling
public class SchedulingConfig {
}
