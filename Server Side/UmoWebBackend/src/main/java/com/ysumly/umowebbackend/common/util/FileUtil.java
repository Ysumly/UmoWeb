package com.ysumly.umowebbackend.common.util;

import com.ysumly.umowebbackend.common.constant.ContentType;
import com.ysumly.umowebbackend.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class FileUtil {

    private static final Pattern SAFE_PATH_SEGMENT =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,199}");
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS =
            Set.of("jpg", "png", "gif", "webp");

    private final Path storageRoot;

    public FileUtil(@Value("${app.storage-path}") String storagePath) {
        this.storageRoot = Paths.get(storagePath).toAbsolutePath().normalize();
    }

    /** 读取 MD 文件内容。 */
    public String readMarkdown(String relativePath) throws IOException {
        return Files.readString(resolveStoredPath(relativePath), StandardCharsets.UTF_8);
    }

    /** 写入 MD 文件并覆盖旧内容，仅供兼容旧调用。 */
    public void writeMarkdown(String relativePath, String content) throws IOException {
        Path target = resolveStoredPath(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
    }

    /** 在目标文件同目录创建临时文件，返回相对 storageRoot 的路径。 */
    public String writeTemporaryMarkdown(String targetRelativePath, String content) throws IOException {
        Path target = resolveStoredPath(targetRelativePath);
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), tempPrefix(target), ".tmp");
        Files.writeString(temporary, content, StandardCharsets.UTF_8);
        return toRelativePath(temporary);
    }

    /** 备份已有 Markdown 到同目录临时文件；源文件不存在时返回 null。 */
    public String copyToTemporaryMarkdown(String sourceRelativePath) throws IOException {
        Path source = resolveStoredPath(sourceRelativePath);
        if (!Files.exists(source)) {
            return null;
        }
        Files.createDirectories(source.getParent());
        Path temporary = Files.createTempFile(source.getParent(), tempPrefix(source), ".bak");
        Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
        return toRelativePath(temporary);
    }

    /** 将临时文件移动到目标路径；replace=false 时绝不覆盖已有文件。 */
    public void promoteTemporaryMarkdown(String temporaryRelativePath,
                                         String targetRelativePath,
                                         boolean replace) throws IOException {
        Path temporary = resolveStoredPath(temporaryRelativePath);
        Path target = resolveStoredPath(targetRelativePath);
        Files.createDirectories(target.getParent());

        if (!replace && Files.exists(target)) {
            throw new FileAlreadyExistsException(target.toString());
        }
        if (!replace) {
            Files.move(temporary, target);
            return;
        }

        try {
            Files.move(temporary, target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** 删除 MD 文件。 */
    public void deleteMarkdown(String relativePath) throws IOException {
        deleteStoredFile(relativePath);
    }

    /** 删除存储根目录内的任意文件。 */
    public void deleteStoredFile(String relativePath) throws IOException {
        Files.deleteIfExists(resolveStoredPath(relativePath));
    }

    public boolean markdownExists(String relativePath) {
        return Files.exists(resolveStoredPath(relativePath));
    }

    /** 生成 MD 文件存储路径（相对于 storagePath）。 */
    public String buildBodyPath(ContentType type, String slug, String bookSlug) {
        if (type == null) {
            throw new BusinessException(400, "内容类型不能为空");
        }
        String safeSlug = requireSafePathSegment(slug, "slug");
        return switch (type) {
            case NOTE -> "contents/NOTE/" + safeSlug + ".md";
            case BOOK_REVIEW -> "contents/BOOK_REVIEW/" + safeSlug + ".md";
            case NOVEL -> {
                String safeBookSlug = requireSafePathSegment(bookSlug, "bookSlug");
                yield "contents/NOVEL/" + safeBookSlug + "/" + safeSlug + ".md";
            }
        };
    }

    /** 生成图片存储路径: images/{YYYY}/{MM}/{uuid}.{ext}。 */
    public String generateImagePath(String extension) {
        String safeExtension = normalizeImageExtension(extension);
        LocalDate now = LocalDate.now();
        return String.format("images/%04d/%02d/%s.%s",
                now.getYear(), now.getMonthValue(), UUID.randomUUID(), safeExtension);
    }

    /** 仅保留用于数据库展示的原始文件名，不参与路径计算。 */
    public String sanitizeOriginalFilename(String originalFilename, String extension) {
        String fallback = "upload." + normalizeImageExtension(extension);
        if (originalFilename == null || originalFilename.isBlank()) {
            return fallback;
        }
        String sanitized = originalFilename.replace('\\', '/');
        int slash = sanitized.lastIndexOf('/');
        if (slash >= 0) {
            sanitized = sanitized.substring(slash + 1);
        }
        sanitized = sanitized.replaceAll("[\\p{Cntrl}]", "").trim();
        if (sanitized.isBlank() || sanitized.equals(".") || sanitized.equals("..")) {
            return fallback;
        }
        return sanitized.length() <= 500 ? sanitized : sanitized.substring(0, 500);
    }

    public Path resolveStoredPath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new BusinessException(400, "文件路径不能为空");
        }
        String lowerPath = relativePath.toLowerCase(Locale.ROOT);
        if (lowerPath.contains("%2e") || lowerPath.contains("%2f") || lowerPath.contains("%5c")) {
            throw new BusinessException(400, "文件路径包含非法编码");
        }
        try {
            Path relative = Paths.get(relativePath);
            if (relative.isAbsolute()) {
                throw new BusinessException(400, "文件路径必须位于存储目录内");
            }
            Path resolved = storageRoot.resolve(relative).normalize();
            if (resolved.equals(storageRoot) || !resolved.startsWith(storageRoot)) {
                throw new BusinessException(400, "文件路径必须位于存储目录内");
            }
            return resolved;
        } catch (InvalidPathException e) {
            throw new BusinessException(400, "文件路径格式无效");
        }
    }

    /** 存储根目录绝对路径 */
    public Path getStorageRoot() {
        return storageRoot;
    }

    private String requireSafePathSegment(String value, String fieldName) {
        if (value == null || !SAFE_PATH_SEGMENT.matcher(value).matches()) {
            throw new BusinessException(400,
                    fieldName + " 只能包含字母、数字、下划线和连字符，且长度为 1-200");
        }
        return value;
    }

    private String normalizeImageExtension(String extension) {
        String normalized = extension == null
                ? ""
                : extension.toLowerCase(Locale.ROOT).replace(".", "");
        if (!ALLOWED_IMAGE_EXTENSIONS.contains(normalized)) {
            throw new BusinessException(400, "不支持的图片扩展名");
        }
        return normalized;
    }

    private String tempPrefix(Path target) {
        String prefix = "." + target.getFileName();
        return prefix.length() >= 3 ? prefix : prefix + "tmp";
    }

    private String toRelativePath(Path path) {
        return storageRoot.relativize(path).toString().replace('\\', '/');
    }
}
