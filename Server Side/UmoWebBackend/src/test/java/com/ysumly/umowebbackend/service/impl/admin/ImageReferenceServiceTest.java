package com.ysumly.umowebbackend.service.impl.admin;

import com.ysumly.umowebbackend.common.util.FileUtil;
import com.ysumly.umowebbackend.mapper.ContentMapper;
import com.ysumly.umowebbackend.mapper.SiteOptionMapper;
import com.ysumly.umowebbackend.model.entity.SiteOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImageReferenceServiceTest {

    private final ContentMapper contentMapper = mock(ContentMapper.class);
    private final SiteOptionMapper siteOptionMapper = mock(SiteOptionMapper.class);
    private Path storageRoot;
    private FileUtil fileUtil;
    private ImageReferenceService service;

    @BeforeEach
    void setUp() {
        storageRoot = Path.of("target", "test-image-reference", UUID.randomUUID().toString());
        fileUtil = new FileUtil(storageRoot.toString());
        service = new ImageReferenceService(contentMapper, siteOptionMapper, fileUtil);
    }

    @Test
    void collectsReferencesFromAllContentsAndFixedPages() throws Exception {
        writeMarkdown(
                "contents/NOTE/draft.md",
                "草稿图片 ![draft](/images/2026/09/draft.png)");
        writeMarkdown(
                "contents/NOTE/published.md",
                "已发布图片 <img src=\"/images/2026/09/published.png\">");
        when(contentMapper.findAllBodyPaths())
                .thenReturn(List.of(
                        "contents/NOTE/draft.md",
                        "contents/NOTE/published.md"));
        when(siteOptionMapper.findByKey("about_page"))
                .thenReturn(option("about_page", "![about](/images/2026/09/about.png)"));
        when(siteOptionMapper.findByKey("project_page"))
                .thenReturn(option("project_page", "外部图片 https://example.com/cover.png"));

        Set<String> references = service.findReferencedImageUrls();

        assertThat(references).containsExactlyInAnyOrder(
                "/images/2026/09/draft.png",
                "/images/2026/09/published.png",
                "/images/2026/09/about.png");
    }

    @Test
    void ignoresRelativeRemoteAndSimilarImagePaths() throws Exception {
        writeMarkdown(
                "contents/NOTE/paths.md",
                """
                相对路径 images/2026/09/relative.png
                远程路径 https://example.com/images/2026/09/remote.png
                相似路径 /images/2026/09/target.png.backup
                """);
        when(contentMapper.findAllBodyPaths()).thenReturn(List.of("contents/NOTE/paths.md"));
        when(siteOptionMapper.findByKey("about_page")).thenReturn(null);
        when(siteOptionMapper.findByKey("project_page")).thenReturn(null);

        assertThat(service.findReferencedImageUrls())
                .containsExactly("/images/2026/09/target.png.backup");
    }

    private void writeMarkdown(String relativePath, String content) throws Exception {
        Path target = fileUtil.resolveStoredPath(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }

    private SiteOption option(String key, String value) {
        SiteOption option = new SiteOption();
        option.setOptionKey(key);
        option.setOptionValue(value);
        return option;
    }
}
