package com.ysumly.umowebbackend.service.impl;

import com.ysumly.umowebbackend.common.constant.ContentStatus;
import com.ysumly.umowebbackend.common.util.FileUtil;
import com.ysumly.umowebbackend.mapper.ContentMapper;
import com.ysumly.umowebbackend.mapper.ContentSearchMapper;
import com.ysumly.umowebbackend.model.entity.Content;
import com.ysumly.umowebbackend.service.ContentSearchIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;

@Service
public class ContentSearchIndexServiceImpl implements ContentSearchIndexService {

    private static final Logger log = LoggerFactory.getLogger(ContentSearchIndexServiceImpl.class);

    private final ContentMapper contentMapper;
    private final ContentSearchMapper searchMapper;
    private final FileUtil fileUtil;

    public ContentSearchIndexServiceImpl(ContentMapper contentMapper,
                                         ContentSearchMapper searchMapper,
                                         FileUtil fileUtil) {
        this.contentMapper = contentMapper;
        this.searchMapper = searchMapper;
        this.fileUtil = fileUtil;
    }

    @Override
    public void sync(Content content, String body) {
        if (content.getId() == null) {
            throw new IllegalArgumentException("Content id is required for search indexing");
        }
        if (!ContentStatus.PUBLISHED.name().equals(content.getStatus())) {
            searchMapper.deleteByContentId(content.getId());
            return;
        }
        searchMapper.upsert(content.getId(), body != null ? body : "");
    }

    @Override
    @Transactional
    public int rebuildPublished() {
        List<Content> published = contentMapper.findAllPublishedForIndex();
        for (Content content : published) {
            String body = "";
            try {
                body = fileUtil.readMarkdown(content.getBodyPath());
            } catch (IOException e) {
                log.warn("Markdown body is unavailable while rebuilding search index: {} ({})",
                        content.getBodyPath(), e.getMessage());
            }
            sync(content, body);
        }
        searchMapper.deleteNotPublished();
        return published.size();
    }
}
