package com.ysumly.umowebbackend.service.impl.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ysumly.umowebbackend.common.exception.BusinessException;
import com.ysumly.umowebbackend.common.util.FileUtil;
import com.ysumly.umowebbackend.mapper.CategoryMapper;
import com.ysumly.umowebbackend.mapper.ContentCategoryMapper;
import com.ysumly.umowebbackend.mapper.ContentMapper;
import com.ysumly.umowebbackend.mapper.ContentTagMapper;
import com.ysumly.umowebbackend.mapper.TagMapper;
import com.ysumly.umowebbackend.model.dto.ContentSaveRequest;
import com.ysumly.umowebbackend.model.entity.Content;
import com.ysumly.umowebbackend.service.ContentVOMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
                voMapper);
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
                voMapper);

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
