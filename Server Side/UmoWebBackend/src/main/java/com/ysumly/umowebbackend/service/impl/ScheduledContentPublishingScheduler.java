package com.ysumly.umowebbackend.service.impl;

import com.ysumly.umowebbackend.mapper.ContentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class ScheduledContentPublishingScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(ScheduledContentPublishingScheduler.class);
    private static final int BATCH_SIZE = 100;

    private final ContentMapper contentMapper;
    private final ScheduledContentPublisher publisher;
    private final Clock clock;

    public ScheduledContentPublishingScheduler(ContentMapper contentMapper,
                                               ScheduledContentPublisher publisher,
                                               Clock clock) {
        this.contentMapper = contentMapper;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${app.scheduling.poll-interval-ms:30000}",
            initialDelayString = "${app.scheduling.initial-delay-ms:30000}")
    public void publishDue() {
        runOnce(LocalDateTime.now(clock));
    }

    public void runOnce(LocalDateTime now) {
        List<Long> dueIds = contentMapper.findDueScheduledIds(now, BATCH_SIZE);
        for (Long id : dueIds) {
            try {
                publisher.publishOne(id, now);
            } catch (RuntimeException e) {
                log.warn("Failed to publish scheduled content: id={}, reason={}",
                        id, e.getMessage());
            }
        }
    }
}
