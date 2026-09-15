package com.ysumly.umowebbackend.service.impl.admin;

import com.ysumly.umowebbackend.common.exception.BulkOperationException;
import com.ysumly.umowebbackend.common.util.FileUtil;
import com.ysumly.umowebbackend.mapper.CategoryMapper;
import com.ysumly.umowebbackend.mapper.ContentCategoryMapper;
import com.ysumly.umowebbackend.mapper.ContentMapper;
import com.ysumly.umowebbackend.mapper.ContentTagMapper;
import com.ysumly.umowebbackend.mapper.TagMapper;
import com.ysumly.umowebbackend.model.dto.BulkContentRequest;
import com.ysumly.umowebbackend.model.dto.ContentCategoryLink;
import com.ysumly.umowebbackend.model.entity.Category;
import com.ysumly.umowebbackend.model.entity.Content;
import com.ysumly.umowebbackend.service.CategoryHierarchyResolver;
import com.ysumly.umowebbackend.service.ContentSearchIndexService;
import com.ysumly.umowebbackend.service.ContentVOMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class ContentBulkOperationServiceImplTest {

    private final ContentMapper contentMapper = mock(ContentMapper.class);
    private final ContentCategoryMapper contentCategoryMapper = mock(ContentCategoryMapper.class);
    private final ContentTagMapper contentTagMapper = mock(ContentTagMapper.class);
    private final CategoryMapper categoryMapper = mock(CategoryMapper.class);
    private final TagMapper tagMapper = mock(TagMapper.class);
    private final CategoryHierarchyResolver categoryHierarchyResolver =
            mock(CategoryHierarchyResolver.class);
    private final ContentSearchIndexService searchIndexService =
            mock(ContentSearchIndexService.class);
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-09-15T12:00:00Z"),
            ZoneId.of("Asia/Shanghai"));

    private ContentManageServiceImpl service;

    @BeforeEach
    void setUp() {
        FileUtil fileUtil = mock(FileUtil.class);
        ContentVOMapper voMapper = new ContentVOMapper(
                contentCategoryMapper,
                contentTagMapper,
                categoryMapper,
                tagMapper,
                new ObjectMapper());
        service = new ContentManageServiceImpl(
                contentMapper,
                contentCategoryMapper,
                contentTagMapper,
                categoryMapper,
                tagMapper,
                fileUtil,
                voMapper,
                categoryHierarchyResolver,
                searchIndexService,
                clock);
    }

    @Test
    void addCategoriesOnlyInsertsMissingLinks() {
        Content first = content(1L, "DRAFT");
        Content second = content(2L, "PUBLISHED");
        when(contentMapper.findByIds(List.of(1L, 2L))).thenReturn(List.of(first, second));
        when(categoryMapper.findByIds(List.of(10L, 11L)))
                .thenReturn(List.of(category(10L), category(11L)));
        when(contentCategoryMapper.findLinksByContentIds(List.of(1L, 2L)))
                .thenReturn(List.of(link(1L, 10L)));
        BulkContentRequest request = request("ADD_CATEGORIES", List.of(1L, 2L));
        request.setCategoryIds(List.of(10L, 11L));

        var result = service.bulk(request);

        verify(contentCategoryMapper).insertIgnore(2L, 10L);
        verify(contentCategoryMapper).insertIgnore(1L, 11L);
        verify(contentCategoryMapper).insertIgnore(2L, 11L);
        verify(contentCategoryMapper, never()).insertIgnore(1L, 10L);
        assertThat(result.getRequestedCount()).isEqualTo(2);
        assertThat(result.getUpdatedCount()).isEqualTo(2);
        assertThat(result.getUnchangedCount()).isZero();
    }

    @Test
    void removeCategoriesRejectsNovelBeforeWriting() {
        Content novel = content(3L, "DRAFT");
        novel.setType("NOVEL");
        when(contentMapper.findByIds(List.of(3L))).thenReturn(List.of(novel));
        when(categoryMapper.findByIds(List.of(10L))).thenReturn(List.of(category(10L)));
        BulkContentRequest request = request("REMOVE_CATEGORIES", List.of(3L));
        request.setCategoryIds(List.of(10L));

        assertThatThrownBy(() -> service.bulk(request))
                .isInstanceOf(BulkOperationException.class)
                .extracting("code")
                .isEqualTo(409);

        verify(contentCategoryMapper, never()).deleteLinks(anyList(), anyList());
    }

    @Test
    void archiveClearsScheduleAndRemovesSearchIndex() {
        Content published = content(4L, "SCHEDULED");
        published.setScheduledAt(LocalDateTime.of(2026, 9, 16, 10, 0));
        when(contentMapper.findByIds(List.of(4L))).thenReturn(List.of(published));

        var result = service.bulk(request("ARCHIVE", List.of(4L)));

        assertThat(published.getStatus()).isEqualTo("ARCHIVED");
        assertThat(published.getScheduledAt()).isNull();
        verify(contentMapper).archiveByIds(List.of(4L));
        verify(searchIndexService).sync(
                argThat(content -> content.getStatus().equals("ARCHIVED")),
                isNull());
        assertThat(result.getUpdatedCount()).isEqualTo(1);
    }

    @Test
    void restoreDraftRejectsItemsThatAreNotArchivedAtomically() {
        Content archived = content(5L, "ARCHIVED");
        Content draft = content(6L, "DRAFT");
        when(contentMapper.findByIds(List.of(5L, 6L))).thenReturn(List.of(archived, draft));

        assertThatThrownBy(() -> service.bulk(request("RESTORE_DRAFT", List.of(5L, 6L))))
                .isInstanceOf(BulkOperationException.class)
                .extracting("code")
                .isEqualTo(409);

        verify(contentMapper, never()).restoreDraftByIds(anyList());
    }

    @Test
    void missingContentRejectsWholeBatch() {
        when(contentMapper.findByIds(List.of(7L, 8L))).thenReturn(List.of(content(7L, "DRAFT")));

        assertThatThrownBy(() -> service.bulk(request("ARCHIVE", List.of(7L, 8L))))
                .isInstanceOf(BulkOperationException.class)
                .extracting("code")
                .isEqualTo(404);

        verify(contentMapper, never()).archiveByIds(anyList());
    }

    private BulkContentRequest request(String action, List<Long> contentIds) {
        BulkContentRequest request = new BulkContentRequest();
        request.setAction(action);
        request.setContentIds(contentIds);
        return request;
    }

    private Content content(Long id, String status) {
        Content content = new Content();
        content.setId(id);
        content.setTitle("Title " + id);
        content.setSlug("title-" + id);
        content.setType("NOTE");
        content.setStatus(status);
        return content;
    }

    private Category category(Long id) {
        Category category = new Category();
        category.setId(id);
        category.setType("NOTE");
        return category;
    }

    private ContentCategoryLink link(Long contentId, Long categoryId) {
        ContentCategoryLink link = new ContentCategoryLink();
        link.setContentId(contentId);
        link.setCategoryId(categoryId);
        return link;
    }
}
