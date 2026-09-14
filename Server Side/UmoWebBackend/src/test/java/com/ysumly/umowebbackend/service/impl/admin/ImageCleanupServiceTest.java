package com.ysumly.umowebbackend.service.impl.admin;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.ysumly.umowebbackend.common.util.FileUtil;
import com.ysumly.umowebbackend.mapper.ImageCleanupTaskMapper;
import com.ysumly.umowebbackend.model.entity.ImageCleanupTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImageCleanupServiceTest {

    private final ImageCleanupTaskMapper cleanupTaskMapper = mock(ImageCleanupTaskMapper.class);
    private Path storageRoot;
    private FileUtil fileUtil;
    private ImageCleanupService service;
    private Logger serviceLogger;

    @BeforeEach
    void setUp() {
        storageRoot = Path.of("target", "test-image-cleanup", UUID.randomUUID().toString());
        fileUtil = new FileUtil(storageRoot.toString());
        service = new ImageCleanupService(cleanupTaskMapper, fileUtil);
        serviceLogger = (Logger) LoggerFactory.getLogger(ImageCleanupService.class);
        serviceLogger.setLevel(Level.OFF);
    }

    @AfterEach
    void tearDown() {
        serviceLogger.setLevel(Level.INFO);
    }

    @Test
    void removesFileAndQueueRowWhenCleanupSucceeds() throws Exception {
        String path = "images/2026/09/success.png";
        Path file = fileUtil.resolveStoredPath(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "image");
        ImageCleanupTask task = task(1L, path);
        when(cleanupTaskMapper.findAll()).thenReturn(List.of(task));

        service.retryPending();

        assertThat(file).doesNotExist();
        verify(cleanupTaskMapper).delete(1L);
        verify(cleanupTaskMapper, never()).recordFailure(any(), anyString());
    }

    @Test
    void keepsQueueRowAndRecordsFailureWhenFileCannotBeDeleted() throws Exception {
        String path = "images/2026/09/blocked.png";
        Path directory = fileUtil.resolveStoredPath(path);
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("child.txt"), "child");
        ImageCleanupTask task = task(2L, path);
        when(cleanupTaskMapper.findAll()).thenReturn(List.of(task));

        service.retryPending();

        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(cleanupTaskMapper).recordFailure(
                org.mockito.ArgumentMatchers.eq(2L),
                errorCaptor.capture());
        assertThat(errorCaptor.getValue()).contains("DirectoryNotEmptyException");
        verify(cleanupTaskMapper, never()).delete(any());
    }

    private ImageCleanupTask task(Long id, String path) {
        ImageCleanupTask task = new ImageCleanupTask();
        task.setId(id);
        task.setImageId(id);
        task.setPath(path);
        return task;
    }
}
