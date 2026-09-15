package com.ysumly.umowebbackend.service.impl.admin;

import com.ysumly.umowebbackend.common.exception.BusinessException;
import com.ysumly.umowebbackend.common.util.FileUtil;
import com.ysumly.umowebbackend.mapper.ImageMapper;
import com.ysumly.umowebbackend.model.entity.Image;
import com.ysumly.umowebbackend.model.vo.ImageIntegrityReportVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImageIntegrityServiceTest {

    @TempDir
    Path storageRoot;

    private final ImageMapper imageMapper = mock(ImageMapper.class);
    private final ImageReferenceService referenceService = mock(ImageReferenceService.class);
    private ImageIntegrityServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ImageIntegrityServiceImpl(
                imageMapper,
                referenceService,
                new FileUtil(storageRoot.toString()));
    }

    @Test
    void classifiesThreeDisjointIntegrityProblemsWithSources() throws Exception {
        writeStoredFile("images/2026/09/present.png");
        writeStoredFile("images/2026/09/unrecorded-existing.png");
        writeStoredFile("images/2026/09/really-untracked.png");
        when(imageMapper.findAll()).thenReturn(List.of(
                image(1L, "images/2026/09/present.png", "present.png"),
                image(2L, "images/2026/09/missing-file.png", "missing-file.png")));
        when(referenceService.scanReferencesStrict()).thenReturn(List.of(
                reference("/images/2026/09/present.png", "CONTENT", 10L, "Present"),
                reference(
                        "/images/2026/09/unrecorded-existing.png",
                        "CONTENT",
                        10L,
                        "Present"),
                reference(
                        "/images/2026/09/unrecorded-existing.png",
                        "CONTENT",
                        10L,
                        "Present"),
                reference(
                        "/images/2026/09/unrecorded-missing.png",
                        "FIXED_PAGE",
                        null,
                        "About 页面"),
                reference(
                        "/images/2026/09/missing-file.png",
                        "CONTENT",
                        20L,
                        "Missing file")));

        ImageIntegrityReportVO report = service.inspect();

        assertThat(report.getScannedAt()).isNotNull();
        assertThat(report.getCounts().getBrokenReferences()).isEqualTo(2);
        assertThat(report.getCounts().getMissingFiles()).isEqualTo(1);
        assertThat(report.getCounts().getUntrackedFiles()).isEqualTo(1);
        assertThat(report.getCounts().getTotal()).isEqualTo(4);
        assertThat(report.getBrokenReferences())
                .extracting(
                        "url",
                        "sourceType",
                        "sourceId",
                        "sourceLabel")
                .containsExactly(
                        tuple(
                                "/images/2026/09/unrecorded-existing.png",
                                "CONTENT",
                                10L,
                                "Present"),
                        tuple(
                                "/images/2026/09/unrecorded-missing.png",
                                "FIXED_PAGE",
                                null,
                                "About 页面"));
        assertThat(report.getMissingFiles())
                .extracting("id", "url", "originalName")
                .containsExactly(tuple(
                        2L,
                        "/images/2026/09/missing-file.png",
                        "missing-file.png"));
        assertThat(report.getUntrackedFiles())
                .extracting("url")
                .containsExactly("/images/2026/09/really-untracked.png");
    }

    @Test
    void returnsEmptyListsWhenStorageAndDatabaseAreConsistent() throws Exception {
        writeStoredFile("images/2026/09/present.png");
        when(imageMapper.findAll()).thenReturn(List.of(
                image(1L, "images/2026/09/present.png", "present.png")));
        when(referenceService.scanReferencesStrict()).thenReturn(List.of(
                reference("/images/2026/09/present.png", "CONTENT", 1L, "Present")));

        ImageIntegrityReportVO report = service.inspect();

        assertThat(report.getCounts().getTotal()).isZero();
        assertThat(report.getBrokenReferences()).isEmpty();
        assertThat(report.getMissingFiles()).isEmpty();
        assertThat(report.getUntrackedFiles()).isEmpty();
    }

    @Test
    void scanFailureReturnsInternalServerErrorWithoutPartialReport() throws Exception {
        when(imageMapper.findAll()).thenReturn(List.of());
        when(referenceService.scanReferencesStrict())
                .thenThrow(new IOException("storage unavailable"));

        assertThatThrownBy(() -> service.inspect())
                .isInstanceOf(BusinessException.class)
                .hasMessage("图片一致性检查失败")
                .extracting("code")
                .isEqualTo(500);
    }

    @Test
    void invalidStoredPathIsReportedAsScanFailure() throws Exception {
        when(imageMapper.findAll()).thenReturn(List.of());
        when(referenceService.scanReferencesStrict())
                .thenThrow(new BusinessException(400, "文件路径格式无效"));

        assertThatThrownBy(() -> service.inspect())
                .isInstanceOf(BusinessException.class)
                .hasMessage("图片一致性检查失败")
                .extracting("code")
                .isEqualTo(500);
    }

    private void writeStoredFile(String relativePath) throws IOException {
        Path target = storageRoot.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, "image");
    }

    private Image image(Long id, String path, String originalName) {
        Image image = new Image();
        image.setId(id);
        image.setPath(path);
        image.setOriginalName(originalName);
        return image;
    }

    private ImageReferenceOccurrence reference(
            String url,
            String sourceType,
            Long sourceId,
            String sourceLabel) {
        return new ImageReferenceOccurrence(url, sourceType, sourceId, sourceLabel);
    }
}
