package com.ysumly.umowebbackend.service.impl;

import com.ysumly.umowebbackend.common.constant.ContentStatus;
import com.ysumly.umowebbackend.common.exception.BusinessException;
import com.ysumly.umowebbackend.common.util.FileUtil;
import com.ysumly.umowebbackend.mapper.ContentMapper;
import com.ysumly.umowebbackend.model.entity.Content;
import com.ysumly.umowebbackend.service.ContentSearchIndexService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;

@Service
public class ScheduledContentPublisher {

    private final ContentMapper contentMapper;
    private final FileUtil fileUtil;
    private final ContentSearchIndexService searchIndexService;

    public ScheduledContentPublisher(ContentMapper contentMapper,
                                     FileUtil fileUtil,
                                     ContentSearchIndexService searchIndexService) {
        this.contentMapper = contentMapper;
        this.fileUtil = fileUtil;
        this.searchIndexService = searchIndexService;
    }

    @Transactional
    public void publishOne(Long id, LocalDateTime now) {
        Content content = contentMapper.findById(id);
        if (content == null
                || !ContentStatus.SCHEDULED.name().equals(content.getStatus())
                || content.getScheduledAt() == null
                || content.getScheduledAt().isAfter(now)) {
            return;
        }

        String body;
        try {
            body = fileUtil.readMarkdown(content.getBodyPath());
        } catch (IOException e) {
            throw new BusinessException(
                    500,
                    "计划发布时间到达，但 Markdown 文件不可读取: " + content.getBodyPath(),
                    e);
        }

        LocalDateTime publishedAt = content.getScheduledAt();
        if (contentMapper.publishScheduled(id, publishedAt) == 0) {
            return;
        }
        content.setStatus(ContentStatus.PUBLISHED.name());
        content.setPublishedAt(publishedAt);
        content.setScheduledAt(null);
        searchIndexService.sync(content, body);
    }
}
