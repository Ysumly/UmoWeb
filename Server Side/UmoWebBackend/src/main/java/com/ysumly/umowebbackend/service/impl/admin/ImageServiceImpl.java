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
import com.ysumly.umowebbackend.model.vo.ImageVO;
import com.ysumly.umowebbackend.service.admin.ImageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ImageServiceImpl implements ImageService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );
    private static final Map<String, String> MIME_EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/gif", "gif",
            "image/webp", "webp"
    );

    private final ImageMapper imageMapper;
    private final FileUtil fileUtil;
    private final ImageReferenceService referenceService;
    private final ImageCleanupTaskMapper cleanupTaskMapper;
    private final ImageCleanupService cleanupService;

    public ImageServiceImpl(ImageMapper imageMapper,
                            FileUtil fileUtil,
                            ImageReferenceService referenceService,
                            ImageCleanupTaskMapper cleanupTaskMapper,
                            ImageCleanupService cleanupService) {
        this.imageMapper = imageMapper;
        this.fileUtil = fileUtil;
        this.referenceService = referenceService;
        this.cleanupTaskMapper = cleanupTaskMapper;
        this.cleanupService = cleanupService;
    }

    @Override
    public ImageVO upload(MultipartFile file) {
        cleanupService.retryPending();
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessException(400,
                    "Unsupported file type: " + contentType + ". Allowed: jpg, png, gif, webp");
        }

        String extension = MIME_EXTENSIONS.get(contentType);
        try {
            if (!matchesSignature(file, contentType)) {
                throw new BusinessException(400, "文件内容与声明的 MIME 类型不匹配");
            }
        } catch (IOException e) {
            throw new BusinessException(400, "无法读取上传文件");
        }

        String relativePath = fileUtil.generateImagePath(extension);
        Path targetPath = fileUtil.resolveStoredPath(relativePath);
        try {
            Files.createDirectories(targetPath.getParent());
            file.transferTo(targetPath.toFile());
        } catch (IOException e) {
            throw new RuntimeException("Failed to save image: " + relativePath, e);
        }

        Image image = new Image();
        image.setOriginalName(fileUtil.sanitizeOriginalFilename(file.getOriginalFilename(), extension));
        image.setStoredName(targetPath.getFileName().toString());
        image.setPath(relativePath);
        image.setSize(file.getSize());
        image.setContentType(contentType);
        image.setWidth(null);   // 暂不解析尺寸
        image.setHeight(null);
        try {
            imageMapper.insert(image);
        } catch (RuntimeException e) {
            try {
                fileUtil.deleteMarkdown(relativePath);
            } catch (IOException cleanupError) {
                e.addSuppressed(cleanupError);
            }
            throw e;
        }

        // 4. 返回 VO
        ImageVO vo = new ImageVO();
        vo.setId(image.getId());
        vo.setUrl("/" + relativePath);
        vo.setOriginalName(image.getOriginalName());
        vo.setSize(image.getSize());
        return vo;
    }

    @Override
    public PageResult<ImageManageVO> list(ImageQuery query) {
        cleanupService.retryPending();
        Set<String> references = referenceService.findReferencedImageUrls();
        List<ImageManageVO> filtered = imageMapper.findAll().stream()
                .map(image -> toManageVO(image, references))
                .filter(image -> matchesUsage(image, query.getUsage()))
                .toList();

        long offset = ((long) query.getPage() - 1) * query.getSize();
        if (offset >= filtered.size()) {
            return new PageResult<>(List.of(), query.getPage(), query.getSize(), filtered.size());
        }
        int from = (int) offset;
        int to = Math.min(filtered.size(), from + query.getSize());
        return new PageResult<>(
                List.copyOf(filtered.subList(from, to)),
                query.getPage(),
                query.getSize(),
                filtered.size());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Image image = imageMapper.findById(id);
        if (image == null) {
            throw new NotFoundException("Image not found: id=" + id);
        }
        if (referenceService.findReferencedImageUrls().contains("/" + image.getPath())) {
            throw new BusinessException(409, "图片仍被内容引用，无法删除");
        }

        imageMapper.delete(id);
        ImageCleanupTask task = new ImageCleanupTask();
        task.setImageId(id);
        task.setPath(image.getPath());
        cleanupTaskMapper.insert(task);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cleanupService.retryPending();
                }
            });
        } else {
            cleanupService.retryPending();
        }
    }

    private ImageManageVO toManageVO(Image image, Set<String> references) {
        ImageManageVO vo = new ImageManageVO();
        vo.setId(image.getId());
        vo.setUrl("/" + image.getPath());
        vo.setOriginalName(image.getOriginalName());
        vo.setSize(image.getSize());
        vo.setContentType(image.getContentType());
        vo.setCreatedAt(image.getCreatedAt());
        vo.setReferenced(references.contains("/" + image.getPath()));
        return vo;
    }

    private boolean matchesUsage(ImageManageVO image, String usage) {
        if (usage == null || usage.isBlank()) {
            return true;
        }
        return "REFERENCED".equals(usage) == image.isReferenced();
    }

    private boolean matchesSignature(MultipartFile file, String contentType) throws IOException {
        byte[] header;
        try (var inputStream = file.getInputStream()) {
            header = inputStream.readNBytes(12);
        }
        return switch (contentType) {
            case "image/jpeg" -> hasPrefix(header, 0xFF, 0xD8, 0xFF);
            case "image/png" -> hasPrefix(
                    header, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "image/gif" -> hasPrefix(header, "GIF87a") || hasPrefix(header, "GIF89a");
            case "image/webp" -> hasPrefix(header, "RIFF")
                    && header.length >= 12
                    && "WEBP".equals(new String(header, 8, 4, StandardCharsets.US_ASCII));
            default -> false;
        };
    }

    private boolean hasPrefix(byte[] bytes, int... expected) {
        if (bytes.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((bytes[i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean hasPrefix(byte[] bytes, String expected) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length < expectedBytes.length) {
            return false;
        }
        for (int i = 0; i < expectedBytes.length; i++) {
            if (bytes[i] != expectedBytes[i]) {
                return false;
            }
        }
        return true;
    }
}
