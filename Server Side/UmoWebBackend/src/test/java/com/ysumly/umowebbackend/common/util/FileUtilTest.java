package com.ysumly.umowebbackend.common.util;

import com.ysumly.umowebbackend.common.constant.ContentType;
import com.ysumly.umowebbackend.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FileUtilTest {

    @TempDir
    Path storageRoot;

    private FileUtil fileUtil;

    @BeforeEach
    void setUp() {
        fileUtil = new FileUtil(storageRoot.toString());
    }

    @Test
    void buildBodyPathRejectsTraversalAndAbsoluteSegments() {
        assertThatThrownBy(() -> fileUtil.buildBodyPath(ContentType.NOTE, "../outside", ""))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> fileUtil.buildBodyPath(ContentType.NOTE, "C:\\outside", ""))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> fileUtil.buildBodyPath(ContentType.NOTE, "/tmp/outside", ""))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> fileUtil.buildBodyPath(ContentType.NOTE, "..\\outside", ""))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> fileUtil.buildBodyPath(ContentType.NOVEL, "safe", "..%2foutside"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> fileUtil.buildBodyPath(ContentType.NOTE, null, ""))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void fileOperationsRejectPathsOutsideStorageRoot() {
        assertThatThrownBy(() -> fileUtil.readMarkdown("../outside.md"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> fileUtil.writeTemporaryMarkdown("../outside.md", "secret"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> fileUtil.deleteMarkdown("../outside.md"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> fileUtil.readMarkdown("..%2foutside.md"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void promotingNewMarkdownDoesNotOverwriteExistingFile() throws IOException {
        Path existing = storageRoot.resolve("contents/NOTE/existing.md");
        Files.createDirectories(existing.getParent());
        Files.writeString(existing, "old");

        String temporary = fileUtil.writeTemporaryMarkdown(
                "contents/NOTE/existing.md", "new");

        assertThatThrownBy(() -> fileUtil.promoteTemporaryMarkdown(
                temporary, "contents/NOTE/existing.md", false))
                .isInstanceOf(IOException.class);
        assertThat(Files.readString(existing)).isEqualTo("old");
    }

    @Test
    void promotingReplacementAtomicallyUpdatesExistingFile() throws IOException {
        String target = "contents/NOTE/article.md";
        fileUtil.writeTemporaryMarkdown(target, "old");
        fileUtil.promoteTemporaryMarkdown(
                fileUtil.writeTemporaryMarkdown(target, "old"), target, false);

        String temporary = fileUtil.writeTemporaryMarkdown(target, "new");
        fileUtil.promoteTemporaryMarkdown(temporary, target, true);

        assertThat(fileUtil.readMarkdown(target)).isEqualTo("new");
    }

    @Test
    void createsTemporaryBackupWithOriginalBytes() throws IOException {
        Path target = storageRoot.resolve("contents/NOTE/article.md");
        Files.createDirectories(target.getParent());
        Files.writeString(target, "old");

        String backup = fileUtil.copyToTemporaryMarkdown("contents/NOTE/article.md");

        assertThat(backup).isNotBlank();
        assertThat(fileUtil.readMarkdown(backup)).isEqualTo("old");
    }

    @Test
    void listsOnlyRegularFilesInsideStoredDirectory() throws IOException {
        Path images = storageRoot.resolve("images");
        Path nested = images.resolve("2026/09");
        Files.createDirectories(nested);
        Files.writeString(nested.resolve("one.png"), "image");
        Files.createDirectories(images.resolve("empty"));

        Path outside = storageRoot.resolve("outside.png");
        Files.writeString(outside, "outside");
        Path link = nested.resolve("linked.png");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "symbolic links are not available");
        }

        assertThat(fileUtil.listStoredFiles("images"))
                .containsExactly("images/2026/09/one.png");
    }

    @Test
    void listingMissingStoredDirectoryReturnsEmptyList() throws IOException {
        assertThat(fileUtil.listStoredFiles("images")).isEmpty();
    }

    @Test
    void listingStoredFileAsDirectoryFails() throws IOException {
        Path images = storageRoot.resolve("images");
        Files.writeString(images, "not-a-directory");

        assertThatThrownBy(() -> fileUtil.listStoredFiles("images"))
                .isInstanceOf(IOException.class);
    }
}
