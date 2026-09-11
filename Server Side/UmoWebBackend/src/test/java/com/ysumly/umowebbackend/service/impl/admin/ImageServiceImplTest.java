package com.ysumly.umowebbackend.service.impl.admin;

import com.ysumly.umowebbackend.common.exception.BusinessException;
import com.ysumly.umowebbackend.common.util.FileUtil;
import com.ysumly.umowebbackend.mapper.ImageMapper;
import com.ysumly.umowebbackend.model.entity.Image;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ImageServiceImplTest {

    private static final byte[] PNG_BYTES = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0
    };

    @TempDir
    Path storageRoot;

    private final ImageMapper imageMapper = mock(ImageMapper.class);
    private FileUtil fileUtil;
    private ImageServiceImpl service;

    @BeforeEach
    void setUp() {
        fileUtil = new FileUtil(storageRoot.toString());
        service = new ImageServiceImpl(imageMapper, fileUtil);
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
}
