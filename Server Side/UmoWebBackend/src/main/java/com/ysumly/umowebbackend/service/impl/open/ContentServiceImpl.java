package com.ysumly.umowebbackend.service.impl.open;

import com.ysumly.umowebbackend.common.exception.NotFoundException;
import com.ysumly.umowebbackend.common.util.FileUtil;
import com.ysumly.umowebbackend.mapper.ContentMapper;
import com.ysumly.umowebbackend.model.dto.ContentQuery;
import com.ysumly.umowebbackend.model.dto.PageResult;
import com.ysumly.umowebbackend.model.entity.Content;
import com.ysumly.umowebbackend.model.vo.ContentDetailVO;
import com.ysumly.umowebbackend.model.vo.ContentListVO;
import com.ysumly.umowebbackend.model.vo.ContentNeighborVO;
import com.ysumly.umowebbackend.service.CategoryHierarchyResolver;
import com.ysumly.umowebbackend.service.ContentVOMapper;
import com.ysumly.umowebbackend.service.SearchExcerptService;
import com.ysumly.umowebbackend.service.open.ContentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class ContentServiceImpl implements ContentService {

    private static final int RELATED_CONTENT_LIMIT = 4;
    private static final Logger log = LoggerFactory.getLogger(ContentServiceImpl.class);

    private final ContentMapper contentMapper;
    private final FileUtil fileUtil;
    private final ContentVOMapper voMapper;
    private final CategoryHierarchyResolver categoryHierarchyResolver;
    private final SearchExcerptService searchExcerptService;

    public ContentServiceImpl(ContentMapper contentMapper,
                              FileUtil fileUtil,
                              ContentVOMapper voMapper,
                              CategoryHierarchyResolver categoryHierarchyResolver,
                              SearchExcerptService searchExcerptService) {
        this.contentMapper = contentMapper;
        this.fileUtil = fileUtil;
        this.voMapper = voMapper;
        this.categoryHierarchyResolver = categoryHierarchyResolver;
        this.searchExcerptService = searchExcerptService;
    }

    @Override
    public PageResult<ContentListVO> listPublished(ContentQuery query) {
        List<Long> categoryIds = categoryHierarchyResolver.resolve(query);
        List<Content> contents = contentMapper.findPublished(query, categoryIds);
        long total = contentMapper.countPublished(query, categoryIds);
        List<ContentListVO> items = assembleListVO(contents);
        return new PageResult<>(items, query.getPage(), query.getSize(), total);
    }

    @Override
    public ContentDetailVO getBySlug(String slug) {
        Content content = contentMapper.findBySlug(slug);
        if (content == null) {
            throw new NotFoundException("Content not found: " + slug);
        }
        String body = "";
        try {
            body = fileUtil.readMarkdown(content.getBodyPath());
        } catch (IOException e) {
            body = "";
        }
        ContentDetailVO detail = voMapper.toDetailVO(content, body);
        Content previous = null;
        Content next = null;
        if (content.getPublishedAt() != null) {
            previous = contentMapper.findPreviousPublished(
                    content.getPublishedAt(),
                    content.getId());
            next = contentMapper.findNextPublished(
                    content.getPublishedAt(),
                    content.getId());
            detail.setPrevious(toNeighborVO(previous));
            detail.setNext(toNeighborVO(next));
        }
        List<Long> excludedIds = new ArrayList<>(2);
        if (previous != null) {
            excludedIds.add(previous.getId());
        }
        if (next != null) {
            excludedIds.add(next.getId());
        }
        try {
            detail.setRelated(voMapper.toListVOs(contentMapper.findRelatedPublished(
                    content.getId(),
                    content.getType(),
                    excludedIds,
                    RELATED_CONTENT_LIMIT)));
        } catch (RuntimeException exception) {
            log.warn(
                    "Failed to load related contents for {}: {}",
                    content.getSlug(),
                    exception.toString());
            detail.setRelated(List.of());
        }
        return detail;
    }

    @Override
    public PageResult<ContentListVO> search(ContentQuery query) {
        String q = query.getQ() != null ? query.getQ() : "";
        List<Content> contents = contentMapper.search(q, query.getOffset(), query.getSize());
        long total = contentMapper.countSearch(q);
        List<ContentListVO> items = assembleListVO(contents);
        if (!q.isBlank()) {
            for (int index = 0; index < contents.size(); index++) {
                Content content = contents.get(index);
                items.get(index).setExcerpt(searchExcerptService.build(
                        content.getSearchBody(),
                        q,
                        content.getSummary()));
            }
        }
        return new PageResult<>(items, query.getPage(), query.getSize(), total);
    }

    // ---- 内部组装逻辑 ----

    private List<ContentListVO> assembleListVO(List<Content> contents) {
        return voMapper.toListVOs(contents);
    }

    private ContentNeighborVO toNeighborVO(Content content) {
        if (content == null) {
            return null;
        }
        ContentNeighborVO vo = new ContentNeighborVO();
        vo.setId(content.getId());
        vo.setTitle(content.getTitle());
        vo.setSlug(content.getSlug());
        vo.setPublishedAt(content.getPublishedAt());
        return vo;
    }
}
