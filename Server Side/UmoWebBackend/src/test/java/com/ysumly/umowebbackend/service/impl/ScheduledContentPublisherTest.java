package com.ysumly.umowebbackend.service.impl;

import com.ysumly.umowebbackend.common.util.FileUtil;
import com.ysumly.umowebbackend.mapper.ContentMapper;
import com.ysumly.umowebbackend.model.entity.Content;
import com.ysumly.umowebbackend.service.ContentSearchIndexService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ScheduledContentPublisherTest {

    private final ContentMapper contentMapper = mock(ContentMapper.class);
    private final FileUtil fileUtil = mock(FileUtil.class);
    private final ContentSearchIndexService searchIndexService =
            mock(ContentSearchIndexService.class);
    private final ScheduledContentPublisher publisher = new ScheduledContentPublisher(
            contentMapper, fileUtil, searchIndexService);

    @Test
    void publishesDueContentAtItsScheduledTimeAndSynchronizesIndex() throws IOException {
        LocalDateTime scheduledAt = LocalDateTime.of(2026, 9, 15, 20, 0);
        LocalDateTime now = scheduledAt.plusSeconds(30);
        Content content = scheduledContent(scheduledAt);
        when(contentMapper.findById(1L)).thenReturn(content);
        when(fileUtil.readMarkdown("contents/NOTE/scheduled.md")).thenReturn("# body");
        when(contentMapper.publishScheduled(1L, scheduledAt)).thenReturn(1);

        publisher.publishOne(1L, now);

        verify(contentMapper).publishScheduled(1L, scheduledAt);
        verify(searchIndexService).sync(
                argThat(value -> value.getStatus().equals("PUBLISHED")
                        && scheduledAt.equals(value.getPublishedAt())
                        && value.getScheduledAt() == null),
                eq("# body"));
    }

    @Test
    void doesNotSynchronizeIndexWhenAnotherWorkerAlreadyClaimedContent() throws IOException {
        LocalDateTime scheduledAt = LocalDateTime.of(2026, 9, 15, 20, 0);
        Content content = scheduledContent(scheduledAt);
        when(contentMapper.findById(1L)).thenReturn(content);
        when(fileUtil.readMarkdown("contents/NOTE/scheduled.md")).thenReturn("# body");
        when(contentMapper.publishScheduled(1L, scheduledAt)).thenReturn(0);

        publisher.publishOne(1L, scheduledAt.plusSeconds(1));

        verify(searchIndexService, never()).sync(any(), any());
    }

    @Test
    void missingMarkdownLeavesScheduledContentUnchanged() throws IOException {
        LocalDateTime scheduledAt = LocalDateTime.of(2026, 9, 15, 20, 0);
        Content content = scheduledContent(scheduledAt);
        when(contentMapper.findById(1L)).thenReturn(content);
        when(fileUtil.readMarkdown("contents/NOTE/scheduled.md"))
                .thenThrow(new IOException("missing"));

        assertThatThrownBy(() -> publisher.publishOne(1L, scheduledAt.plusSeconds(1)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("计划发布时间到达，但 Markdown 文件不可读取");

        verify(contentMapper, never()).publishScheduled(any(), any());
        verify(searchIndexService, never()).sync(any(), any());
    }

    private Content scheduledContent(LocalDateTime scheduledAt) {
        Content content = new Content();
        content.setId(1L);
        content.setStatus("SCHEDULED");
        content.setScheduledAt(scheduledAt);
        content.setBodyPath("contents/NOTE/scheduled.md");
        return content;
    }
}
