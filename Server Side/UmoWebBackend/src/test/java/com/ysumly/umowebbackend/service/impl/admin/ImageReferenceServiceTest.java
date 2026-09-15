package com.ysumly.umowebbackend.service.impl.admin;

import com.ysumly.umowebbackend.common.util.FileUtil;
import com.ysumly.umowebbackend.mapper.ContentMapper;
import com.ysumly.umowebbackend.mapper.SiteOptionMapper;
import com.ysumly.umowebbackend.model.entity.Content;
import com.ysumly.umowebbackend.model.entity.SiteOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        when(contentMapper.findAllForReferenceScan())
                .thenReturn(List.of(
                        content(1L, "Draft", "contents/NOTE/draft.md"),
                        content(2L, "Published", "contents/NOTE/published.md")));
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
        when(contentMapper.findAllForReferenceScan())
                .thenReturn(List.of(content(1L, "Paths", "contents/NOTE/paths.md")));
        when(siteOptionMapper.findByKey("about_page")).thenReturn(null);
        when(siteOptionMapper.findByKey("project_page")).thenReturn(null);

        assertThat(service.findReferencedImageUrls())
                .containsExactly("/images/2026/09/target.png.backup");
    }

    @Test
    void strictScanReturnsReferenceSourcesForContentsAndFixedPages() throws Exception {
        writeMarkdown(
                "contents/NOTE/draft.md",
                "![draft](/images/2026/09/draft.png)");
        writeMarkdown(
                "contents/NOTE/published.md",
                "![published](/images/2026/09/published.png)");
        when(contentMapper.findAllForReferenceScan()).thenReturn(List.of(
                content(10L, "Draft title", "contents/NOTE/draft.md"),
                content(20L, "Published title", "contents/NOTE/published.md")));
        when(siteOptionMapper.findByKey("about_page"))
                .thenReturn(option("about_page", "![about](/images/2026/09/about.png)"));
        when(siteOptionMapper.findByKey("project_page"))
                .thenReturn(option("project_page", "![project](/images/2026/09/project.png)"));

        assertThat(service.scanReferencesStrict()).containsExactly(
                new ImageReferenceOccurrence(
                        "/images/2026/09/draft.png", "CONTENT", 10L, "Draft title"),
                new ImageReferenceOccurrence(
                        "/images/2026/09/published.png", "CONTENT", 20L, "Published title"),
                new ImageReferenceOccurrence(
                        "/images/2026/09/about.png", "FIXED_PAGE", null, "About 页面"),
                new ImageReferenceOccurrence(
                        "/images/2026/09/project.png", "FIXED_PAGE", null, "Project 页面"));
    }

    @Test
    void strictScanFailsWhenContentCannotBeRead() {
        when(contentMapper.findAllForReferenceScan()).thenReturn(List.of(
                content(1L, "Missing body", "contents/NOTE/missing.md")));
        when(siteOptionMapper.findByKey("about_page")).thenReturn(null);
        when(siteOptionMapper.findByKey("project_page")).thenReturn(null);

        assertThatThrownBy(() -> service.scanReferencesStrict())
                .isInstanceOf(IOException.class);
    }

    @Test
    void forgivingScanStillIgnoresUnreadableContent() {
        when(contentMapper.findAllForReferenceScan()).thenReturn(List.of(
                content(1L, "Missing body", "contents/NOTE/missing.md")));
        when(siteOptionMapper.findByKey("about_page")).thenReturn(null);
        when(siteOptionMapper.findByKey("project_page")).thenReturn(null);

        assertThat(service.findReferencedImageUrls()).isEmpty();
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

    private Content content(Long id, String title, String bodyPath) {
        Content content = new Content();
        content.setId(id);
        content.setTitle(title);
        content.setBodyPath(bodyPath);
        return content;
    }
}
