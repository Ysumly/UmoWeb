package com.ysumly.umowebbackend.service.impl;

import com.ysumly.umowebbackend.mapper.ContentMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.Mockito.*;

class ScheduledContentPublishingSchedulerTest {

    private final ContentMapper contentMapper = mock(ContentMapper.class);
    private final ScheduledContentPublisher publisher = mock(ScheduledContentPublisher.class);
    private final ScheduledContentPublishingScheduler scheduler =
            new ScheduledContentPublishingScheduler(
                    contentMapper,
                    publisher,
                    Clock.system(ZoneId.of("Asia/Shanghai")));

    @Test
    void continuesPublishingLaterItemsWhenOneItemFails() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 15, 20, 0);
        when(contentMapper.findDueScheduledIds(now, 100)).thenReturn(List.of(1L, 2L));
        doThrow(new IllegalStateException("missing file"))
                .when(publisher).publishOne(1L, now);

        scheduler.runOnce(now);

        verify(publisher).publishOne(1L, now);
        verify(publisher).publishOne(2L, now);
    }
}
