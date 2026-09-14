package com.ysumly.umowebbackend.service.impl.admin;

import com.ysumly.umowebbackend.common.exception.BusinessException;
import com.ysumly.umowebbackend.common.exception.NotFoundException;
import com.ysumly.umowebbackend.common.util.FileUtil;
import com.ysumly.umowebbackend.mapper.ImageCleanupTaskMapper;
import com.ysumly.umowebbackend.mapper.ImageMapper;
import com.ysumly.umowebbackend.model.dto.ImageQuery;
import com.ysumly.umowebbackend.model.dto.PageResult;
import com.ysumly.umowebbackend.model.entity.Image;
import com.ysumly.umowebbackend.model.entity.ImageCleanupTask;
import com.ysumly.umowebbackend.model.vo.ImageManageVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ImageServiceImplTest {

    private static final byte[] PNG_BYTES = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0
    };

    private final ImageMapper imageMapper = mock(ImageMapper.class);
    private final ImageCleanupTaskMapper cleanupTaskMapper = mock(ImageCleanupTaskMapper.class);
    private final ImageReferenceService referenceService = mock(ImageReferenceService.class);
    private final ImageCleanupService cleanupService = mock(ImageCleanupService.class);
    private Path storageRoot;
    private FileUtil fileUtil;
    private ImageServiceImpl service;

    @BeforeEach
    void setUp() {
        storageRoot = Path.of("target", "test-images", UUID.randomUUID().toString());
        fileUtil = new FileUtil(storageRoot.toString());
        service = new ImageServiceImpl(
                imageMapper,
                fileUtil,
                referenceService,
                cleanupTaskMapper,
                cleanupService);
    }

    @Test
    void uploadRejectsForgedMimeType() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "fake.png", "image/png", "not-an-image".getBytes());

        assertThatThrownBy(() -> service.upload(file))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(400);
        verify(imageMapper, never()).insert(any());
    }

    @Test
    void uploadUsesFixedExtensionAndSanitizesOriginalFilename() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "../evil.exe", "image/png", PNG_BYTES);

        service.upload(file);

        ArgumentCaptor<Image> captor = ArgumentCaptor.forClass(Image.class);
        verify(imageMapper).insert(captor.capture());
        Image image = captor.getValue();
        assertThat(image.getOriginalName()).isEqualTo("evil.exe");
        assertThat(image.getPath()).endsWith(".png");
        assertThat(image.getStoredName()).endsWith(".png");
        assertThatThrownBy(() -> fileUtil.markdownExists("../outside.png"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void uploadHandlesMissingOriginalFilename() {
        MockMultipartFile file = new MockMultipartFile(
                "file", null, "image/png", PNG_BYTES);

        service.upload(file);

        ArgumentCaptor<Image> captor = ArgumentCaptor.forClass(Image.class);
        verify(imageMapper).insert(captor.capture());
        assertThat(captor.getValue().getOriginalName()).isEqualTo("upload.png");
    }

    @Test
    void uploadDatabaseFailureDeletesWrittenImage() throws Exception {
        doThrow(new IllegalStateException("db unavailable"))
                .when(imageMapper).insert(any(Image.class));
        MockMultipartFile file = new MockMultipartFile(
                "file", "image.png", "image/png", PNG_BYTES);

        assertThatThrownBy(() -> service.upload(file))
                .isInstanceOf(IllegalStateException.class);

        try (var paths = Files.walk(storageRoot)) {
            assertThat(paths.filter(Files::isRegularFile)).isEmpty();
        }
    }

    @Test
    void listsImagesWithReferenceStatusAndPagination() {
        when(imageMapper.findAll()).thenReturn(List.of(
                image(3L, "images/2026/09/c.png", "c.png"),
                image(2L, "images/2026/09/b.png", "b.png"),
                image(1L, "images/2026/09/a.png", "a.png")));
        when(referenceService.findReferencedImageUrls())
                .thenReturn(Set.of("/images/2026/09/b.png"));
        ImageQuery query = query(1, 2, null);

        PageResult<ImageManageVO> result = service.list(query);

        assertThat(result.getTotal()).isEqualTo(3);
        assertThat(result.getItems())
                .extracting(ImageManageVO::getId, ImageManageVO::isReferenced)
                .containsExactly(
                        tuple(3L, false),
                        tuple(2L, true));
        verify(cleanupService).retryPending();
    }

    @Test
    void filtersOrphanedImagesBeforePagination() {
        when(imageMapper.findAll()).thenReturn(List.of(
                image(3L, "images/2026/09/c.png", "c.png"),
                image(2L, "images/2026/09/b.png", "b.png"),
                image(1L, "images/2026/09/a.png", "a.png")));
        when(referenceService.findReferencedImageUrls())
                .thenReturn(Set.of("/images/2026/09/b.png"));

        PageResult<ImageManageVO> result = service.list(query(1, 1, "ORPHANED"));

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getItems())
                .extracting(ImageManageVO::getId)
                .containsExactly(3L);
    }

    @Test
    void deleteMissingImageReturnsNotFound() {
        when(imageMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(NotFoundException.class)
                .extracting("code")
                .isEqualTo(404);
        verify(imageMapper, never()).delete(anyLong());
    }

    @Test
    void deleteReferencedImageReturnsConflictWithoutRemovingRecord() {
        Image image = image(1L, "images/2026/09/a.png", "a.png");
        when(imageMapper.findById(1L)).thenReturn(image);
        when(referenceService.findReferencedImageUrls())
                .thenReturn(Set.of("/images/2026/09/a.png"));

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("图片仍被内容引用，无法删除")
                .extracting("code")
                .isEqualTo(409);
        verify(imageMapper, never()).delete(anyLong());
        verify(cleanupTaskMapper, never()).insert(any());
    }

    @Test
    void deleteUnreferencedImageQueuesPathAndCleansAfterCommit() {
        Image image = image(1L, "images/2026/09/a.png", "a.png");
        when(imageMapper.findById(1L)).thenReturn(image);
        when(referenceService.findReferencedImageUrls()).thenReturn(Set.of());
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.delete(1L);

            verify(imageMapper).delete(1L);
            ArgumentCaptor<ImageCleanupTask> captor =
                    ArgumentCaptor.forClass(ImageCleanupTask.class);
            verify(cleanupTaskMapper).insert(captor.capture());
            assertThat(captor.getValue().getImageId()).isEqualTo(1L);
            assertThat(captor.getValue().getPath()).isEqualTo("images/2026/09/a.png");
            verify(cleanupService, never()).retryPending();

            completeSynchronization(TransactionSynchronization.STATUS_COMMITTED);
            verify(cleanupService).retryPending();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private ImageQuery query(int page, int size, String usage) {
        ImageQuery query = new ImageQuery();
        query.setPage(page);
        query.setSize(size);
        query.setUsage(usage);
        return query;
    }

    private Image image(Long id, String path, String storedName) {
        Image image = new Image();
        image.setId(id);
        image.setPath(path);
        image.setStoredName(storedName);
        image.setOriginalName(storedName);
        image.setContentType("image/png");
        image.setSize(100L);
        image.setCreatedAt(LocalDateTime.of(2026, 9, 13, 10, id.intValue()));
        return image;
    }

    private void completeSynchronization(int status) {
        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
            synchronization.afterCompletion(status);
        }
    }
}
