package com.ysumly.umowebbackend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulingConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SchedulingConfig.class);

    @Test
    void schedulingCanBeDisabledForNonWebBackfillProcesses() {
        contextRunner
                .withPropertyValues("app.scheduling.enabled=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class));
    }
}
