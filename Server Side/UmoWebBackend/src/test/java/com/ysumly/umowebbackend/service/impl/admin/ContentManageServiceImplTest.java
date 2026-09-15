package com.ysumly.umowebbackend.service.impl.admin;

import com.ysumly.umowebbackend.common.exception.BusinessException;
import com.ysumly.umowebbackend.common.util.FileUtil;
import com.ysumly.umowebbackend.mapper.CategoryMapper;
import com.ysumly.umowebbackend.mapper.ContentCategoryMapper;
import com.ysumly.umowebbackend.mapper.ContentMapper;
import com.ysumly.umowebbackend.mapper.ContentTagMapper;
import com.ysumly.umowebbackend.mapper.TagMapper;
import com.ysumly.umowebbackend.model.dto.ContentSaveRequest;
import com.ysumly.umowebbackend.model.dto.ContentQuery;
import com.ysumly.umowebbackend.model.entity.Category;
import com.ysumly.umowebbackend.model.entity.Content;
import com.ysumly.umowebbackend.model.entity.Tag;
import com.ysumly.umowebbackend.service.ContentSearchIndexService;
import com.ysumly.umowebbackend.service.ContentVOMapper;
import com.ysumly.umowebbackend.service.CategoryHierarchyResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ContentManageServiceImplTest {

    @TempDir
    Path storageRoot;

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

    private FileUtil fileUtil;
    private ContentVOMapper voMapper;
    private ContentManageServiceImpl service;

    @BeforeEach
    void setUp() {
        fileUtil = new FileUtil(storageRoot.toString());
        voMapper = new ContentVOMapper(
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
        when(contentCategoryMapper.findLinksByContentIds(anyList())).thenReturn(List.of());
        when(contentTagMapper.findLinksByContentIds(anyList())).thenReturn(List.of());
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void createRejectsDuplicateSlugWithoutOverwritingExistingMarkdown() throws IOException {
        Path existing = storageRoot.resolve("contents/NOTE/duplicate.md");
        Files.createDirectories(existing.getParent());
        Files.writeString(existing, "old");
        when(contentMapper.countBySlug(eq("duplicate"), isNull())).thenReturn(1L);

        ContentSaveRequest request = noteRequest("duplicate", "new");

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(409);
        assertThat(Files.readString(existing)).isEqualTo("old");
        verify(contentMapper, never()).insert(any());
    }

    @Test
    void createDatabaseFailureCleansTemporaryFile() throws IOException {
        when(contentMapper.countBySlug("new-note", null)).thenReturn(0L);
        doThrow(new IllegalStateException("db unavailable"))
                .when(contentMapper).insert(any(Content.class));

        ContentSaveRequest request = noteRequest("new-note", "body");

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalStateException.class);
        assertThat(fileUtil.markdownExists("contents/NOTE/new-note.md")).isFalse();
        assertThat(countFiles(storageRoot)).isZero();
    }

    @Test
    void createRejectsMissingCategoryBeforeWritingFile() throws IOException {
        when(contentMapper.countBySlug("new-note", null)).thenReturn(0L);
        when(categoryMapper.findByIds(List.of(99L))).thenReturn(List.of());
        ContentSaveRequest request = noteRequest("new-note", "body");
        request.setCategoryIds(List.of(99L));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(400);

        verify(contentMapper, never()).insert(any());
        assertThat(countFiles(storageRoot)).isZero();
    }

    @Test
    void createStoresBlankMetadataAsNull() throws IOException {
        when(contentMapper.countBySlug("metadata-note", null)).thenReturn(0L);
        doAnswer(invocation -> {
            Content content = invocation.getArgument(0);
            content.setId(1L);
            return null;
        }).when(contentMapper).insert(any(Content.class));
        ContentSaveRequest request = noteRequest("metadata-note", "body");
        request.setMetadata("   ");

        service.create(request);

        ArgumentCaptor<Content> captor = ArgumentCaptor.forClass(Content.class);
        verify(contentMapper).insert(captor.capture());
        assertThat(captor.getValue().getMetadata()).isNull();
    }

    @Test
    void createStoresMarkdownDatabaseRecordAndAssociations() throws IOException {
        when(contentMapper.countBySlug("new-note", null)).thenReturn(0L);
        when(categoryMapper.findByIds(List.of(1L))).thenReturn(List.of(new Category()));
        when(tagMapper.findByIds(List.of(2L))).thenReturn(List.of(new Tag()));
        doAnswer(invocation -> {
            Content content = invocation.getArgument(0);
            content.setId(7L);
            return null;
        }).when(contentMapper).insert(any(Content.class));

        ContentSaveRequest request = noteRequest("new-note", "# 正文");
        request.setCategoryIds(List.of(1L));
        request.setTagIds(List.of(2L));

        var created = service.create(request);

        assertThat(created.getTitle()).isEqualTo("Title");
        assertThat(created.getBody()).isEqualTo("# 正文");
        assertThat(fileUtil.readMarkdown("contents/NOTE/new-note.md")).isEqualTo("# 正文");
        verify(contentCategoryMapper).insert(7L, 1L);
        verify(contentTagMapper).insert(7L, 2L);
    }

    @Test
    void adminListUsesResolvedCategoryIds() {
        ContentQuery query = new ContentQuery();
        query.setCategoryId(1L);
        query.setIncludeDescendants(true);
        when(categoryHierarchyResolver.resolve(query)).thenReturn(List.of(1L, 2L));
        when(contentMapper.findAll(query, List.of(1L, 2L))).thenReturn(List.of());
        when(contentMapper.countAll(query, List.of(1L, 2L))).thenReturn(0L);

        service.list(query);

        verify(contentMapper).findAll(query, List.of(1L, 2L));
        verify(contentMapper).countAll(query, List.of(1L, 2L));
    }

    @Test
    void updateDatabaseFailureKeepsOldFileAndOldDatabaseState() throws IOException {
        Content old = existingContent(1L, "article", "contents/NOTE/article.md", "old");
        when(contentMapper.findById(1L)).thenReturn(old);
        when(contentMapper.countBySlug("article", 1L)).thenReturn(0L);
        doThrow(new IllegalStateException("db unavailable"))
                .when(contentMapper).update(any(Content.class));

        ContentSaveRequest request = noteRequest("article", "new");

        assertThatThrownBy(() -> service.update(1L, request))
                .isInstanceOf(IllegalStateException.class);
        assertThat(fileUtil.readMarkdown(old.getBodyPath())).isEqualTo("old");
        assertThat(old.getBodyPath()).isEqualTo("contents/NOTE/article.md");
        assertThat(countFiles(storageRoot)).isEqualTo(1);
    }

    @Test
    void updatePathChangeDeletesOldFileOnlyAfterCommit() throws IOException {
        Content old = existingContent(1L, "old-slug", "contents/NOTE/old-slug.md", "old");
        when(contentMapper.findById(1L)).thenReturn(old);
        when(contentMapper.countBySlug("new-slug", 1L)).thenReturn(0L);
        beginSynchronization();

        ContentSaveRequest request = noteRequest("new-slug", "new");
        service.update(1L, request);

        assertThat(fileUtil.markdownExists("contents/NOTE/old-slug.md")).isTrue();
        assertThat(fileUtil.readMarkdown("contents/NOTE/new-slug.md")).isEqualTo("new");

        completeSynchronization(TransactionSynchronization.STATUS_COMMITTED);

        assertThat(fileUtil.markdownExists("contents/NOTE/old-slug.md")).isFalse();
        assertThat(fileUtil.readMarkdown("contents/NOTE/new-slug.md")).isEqualTo("new");
    }

    @Test
    void updateRollbackRestoresOldFileAfterDatabaseCommitFailure() throws IOException {
        Content old = existingContent(1L, "article", "contents/NOTE/article.md", "old");
        when(contentMapper.findById(1L)).thenReturn(old);
        when(contentMapper.countBySlug("article", 1L)).thenReturn(0L);
        beginSynchronization();

        service.update(1L, noteRequest("article", "new"));
        assertThat(fileUtil.readMarkdown(old.getBodyPath())).isEqualTo("new");

        completeSynchronization(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(fileUtil.readMarkdown(old.getBodyPath())).isEqualTo("old");
    }

    @Test
    void updateFileWriteFailureDoesNotChangeDatabase() throws IOException {
        Content old = existingContent(1L, "article", "contents/NOTE/article.md", "old");
        when(contentMapper.findById(1L)).thenReturn(old);
        when(contentMapper.countBySlug("article", 1L)).thenReturn(0L);
        FileUtil failingFileUtil = mock(FileUtil.class);
        when(failingFileUtil.buildBodyPath(any(), eq("article"), any()))
                .thenReturn("contents/NOTE/article.md");
        when(failingFileUtil.copyToTemporaryMarkdown(any()))
                .thenReturn("contents/NOTE/.backup.tmp");
        when(failingFileUtil.writeTemporaryMarkdown(any(), any()))
                .thenThrow(new IOException("disk full"));
        ContentManageServiceImpl failingService = new ContentManageServiceImpl(
                contentMapper,
                contentCategoryMapper,
                contentTagMapper,
                categoryMapper,
                tagMapper,
                failingFileUtil,
                voMapper,
                categoryHierarchyResolver,
                searchIndexService,
                clock);

        assertThatThrownBy(() -> failingService.update(1L, noteRequest("article", "new")))
                .isInstanceOf(RuntimeException.class);
        verify(contentMapper, never()).update(any(Content.class));
    }

    private ContentSaveRequest noteRequest(String slug, String body) {
        ContentSaveRequest request = new ContentSaveRequest();
        request.setTitle("Title");
        request.setSlug(slug);
        request.setBody(body);
        request.setType("NOTE");
        request.setStatus("DRAFT");
        return request;
    }

    @Test
    void createPublishedContentSynchronizesSearchIndexWithSubmittedBody() {
        when(contentMapper.countBySlug("published-note", null)).thenReturn(0L);
        doAnswer(invocation -> {
            Content content = invocation.getArgument(0);
            content.setId(9L);
            return null;
        }).when(contentMapper).insert(any(Content.class));
        ContentSaveRequest request = noteRequest("published-note", "# 可搜索正文");
        request.setStatus("PUBLISHED");

        service.create(request);

        verify(searchIndexService).sync(
                argThat(content -> content.getId().equals(9L)
                        && content.getStatus().equals("PUBLISHED")),
                eq("# 可搜索正文"));
    }

    @Test
    void updateWithdrawnContentSynchronizesDraftStatus() throws IOException {
        Content old = existingContent(3L, "withdraw", "contents/NOTE/withdraw.md", "old");
        old.setStatus("PUBLISHED");
        when(contentMapper.findById(3L)).thenReturn(old);
        when(contentMapper.countBySlug("withdraw", 3L)).thenReturn(0L);
        ContentSaveRequest request = noteRequest("withdraw", "new");

        service.update(3L, request);

        verify(searchIndexService).sync(
                argThat(content -> content.getId().equals(3L)
                        && content.getStatus().equals("DRAFT")),
                eq("new"));
    }

    @Test
    void createScheduledContentRequiresFutureScheduleAndDoesNotPublishIt() throws IOException {
        when(contentMapper.countBySlug("scheduled-note", null)).thenReturn(0L);
        doAnswer(invocation -> {
            Content content = invocation.getArgument(0);
            content.setId(11L);
            return null;
        }).when(contentMapper).insert(any(Content.class));
        ContentSaveRequest request = noteRequest("scheduled-note", "# 计划发布");
        request.setStatus("SCHEDULED");
        request.setScheduledAt(LocalDateTime.of(2026, 9, 15, 21, 0));

        service.create(request);

        ArgumentCaptor<Content> captor = ArgumentCaptor.forClass(Content.class);
        verify(contentMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("SCHEDULED");
        assertThat(captor.getValue().getScheduledAt())
                .isEqualTo(LocalDateTime.of(2026, 9, 15, 21, 0));
        assertThat(captor.getValue().getPublishedAt()).isNull();
        verify(searchIndexService).sync(
                argThat(content -> content.getStatus().equals("SCHEDULED")),
                eq("# 计划发布"));
    }

    @Test
    void createScheduledContentRejectsPastSchedule() {
        when(contentMapper.countBySlug("scheduled-note", null)).thenReturn(0L);
        ContentSaveRequest request = noteRequest("scheduled-note", "body");
        request.setStatus("SCHEDULED");
        request.setScheduledAt(LocalDateTime.of(2026, 9, 15, 19, 59, 59));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("计划发布时间必须晚于当前时间")
                .extracting("code")
                .isEqualTo(400);

        verify(contentMapper, never()).insert(any());
    }

    @Test
    void createRejectsArchivedStatus() {
        when(contentMapper.countBySlug("archived-note", null)).thenReturn(0L);
        ContentSaveRequest request = noteRequest("archived-note", "body");
        request.setStatus("ARCHIVED");

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("新建文章不能直接归档")
                .extracting("code")
                .isEqualTo(400);

        verify(contentMapper, never()).insert(any());
    }

    @Test
    void publishedContentCannotBeMovedBackToScheduled() throws IOException {
        Content old = existingContent(12L, "published", "contents/NOTE/published.md", "body");
        old.setStatus("PUBLISHED");
        old.setPublishedAt(LocalDateTime.of(2026, 9, 14, 10, 0));
        when(contentMapper.findById(12L)).thenReturn(old);
        when(contentMapper.countBySlug("published", 12L)).thenReturn(0L);
        ContentSaveRequest request = noteRequest("published", "body");
        request.setStatus("SCHEDULED");
        request.setScheduledAt(LocalDateTime.of(2026, 9, 16, 10, 0));

        assertThatThrownBy(() -> service.update(12L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只有未发布的草稿可以设置定时发布")
                .extracting("code")
                .isEqualTo(409);

        verify(contentMapper, never()).update(any());
    }

    @Test
    void archivingClearsScheduleAndRetainsPublishedTime() throws IOException {
        LocalDateTime publishedAt = LocalDateTime.of(2026, 9, 14, 10, 0);
        Content old = existingContent(13L, "archive-me", "contents/NOTE/archive-me.md", "body");
        old.setStatus("SCHEDULED");
        old.setPublishedAt(publishedAt);
        old.setScheduledAt(LocalDateTime.of(2026, 9, 16, 10, 0));
        when(contentMapper.findById(13L)).thenReturn(old);
        when(contentMapper.countBySlug("archive-me", 13L)).thenReturn(0L);
        ContentSaveRequest request = noteRequest("archive-me", "body");
        request.setStatus("ARCHIVED");

        service.update(13L, request);

        assertThat(old.getStatus()).isEqualTo("ARCHIVED");
        assertThat(old.getScheduledAt()).isNull();
        assertThat(old.getPublishedAt()).isEqualTo(publishedAt);
        verify(searchIndexService).sync(
                argThat(content -> content.getStatus().equals("ARCHIVED")),
                eq("body"));
    }

    @Test
    void republishingArchivedContentUsesCurrentTime() throws IOException {
        Content old = existingContent(14L, "republish", "contents/NOTE/republish.md", "body");
        old.setStatus("ARCHIVED");
        old.setPublishedAt(LocalDateTime.of(2025, 1, 1, 10, 0));
        when(contentMapper.findById(14L)).thenReturn(old);
        when(contentMapper.countBySlug("republish", 14L)).thenReturn(0L);
        ContentSaveRequest request = noteRequest("republish", "body");
        request.setStatus("PUBLISHED");

        service.update(14L, request);

        assertThat(old.getStatus()).isEqualTo("PUBLISHED");
        assertThat(old.getScheduledAt()).isNull();
        assertThat(old.getPublishedAt()).isEqualTo(LocalDateTime.of(2026, 9, 15, 20, 0));
    }

    private Content existingContent(Long id, String slug, String bodyPath, String body) throws IOException {
        Content content = new Content();
        content.setId(id);
        content.setTitle("Title");
        content.setSlug(slug);
        content.setBodyPath(bodyPath);
        content.setType("NOTE");
        content.setStatus("DRAFT");
        fileUtil.writeMarkdown(bodyPath, body);
        return content;
    }

    private long countFiles(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).count();
        }
    }

    private void beginSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
    }

    private void completeSynchronization(int status) {
        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(status);
        }
        TransactionSynchronizationManager.clearSynchronization();
    }
}
