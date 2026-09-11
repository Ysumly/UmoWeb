package com.ysumly.umowebbackend.service.impl.admin;

import com.ysumly.umowebbackend.common.constant.ContentStatus;
import com.ysumly.umowebbackend.common.constant.ContentType;
import com.ysumly.umowebbackend.common.exception.BusinessException;
import com.ysumly.umowebbackend.common.exception.NotFoundException;
import com.ysumly.umowebbackend.common.util.FileUtil;
import com.ysumly.umowebbackend.mapper.*;
import com.ysumly.umowebbackend.model.dto.ContentQuery;
import com.ysumly.umowebbackend.model.dto.ContentSaveRequest;
import com.ysumly.umowebbackend.model.dto.PageResult;
import com.ysumly.umowebbackend.model.entity.Category;
import com.ysumly.umowebbackend.model.entity.Content;
import com.ysumly.umowebbackend.model.vo.ContentDetailVO;
import com.ysumly.umowebbackend.model.vo.ContentListVO;
import com.ysumly.umowebbackend.service.ContentVOMapper;
import com.ysumly.umowebbackend.service.admin.ContentManageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ContentManageServiceImpl implements ContentManageService {

    private static final Logger log = LoggerFactory.getLogger(ContentManageServiceImpl.class);

    private final ContentMapper contentMapper;
    private final ContentCategoryMapper contentCategoryMapper;
    private final ContentTagMapper contentTagMapper;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final FileUtil fileUtil;
    private final ContentVOMapper voMapper;

    public ContentManageServiceImpl(ContentMapper contentMapper,
                                    ContentCategoryMapper contentCategoryMapper,
                                    ContentTagMapper contentTagMapper,
                                    CategoryMapper categoryMapper,
                                    TagMapper tagMapper,
                                    FileUtil fileUtil,
                                    ContentVOMapper voMapper) {
        this.contentMapper = contentMapper;
        this.contentCategoryMapper = contentCategoryMapper;
        this.contentTagMapper = contentTagMapper;
        this.categoryMapper = categoryMapper;
        this.tagMapper = tagMapper;
        this.fileUtil = fileUtil;
        this.voMapper = voMapper;
    }

    @Override
    public PageResult<ContentListVO> list(ContentQuery query) {
        List<Content> contents = contentMapper.findAll(query);
        long total = contentMapper.countAll(query);
        List<ContentListVO> items = assembleListVO(contents);
        return new PageResult<>(items, query.getPage(), query.getSize(), total);
    }

    @Override
    public ContentDetailVO getById(Long id) {
        Content content = contentMapper.findById(id);
        if (content == null) {
            throw new NotFoundException("Content not found: id=" + id);
        }
        return toDetailVO(content);
    }

    @Override
    @Transactional
    public ContentDetailVO create(ContentSaveRequest request) {
        ContentType type = parseContentType(request.getType());
        ContentStatus status = parseStatus(request.getStatus(), ContentStatus.DRAFT);
        validateAssociationIds(request.getCategoryIds(), request.getTagIds());
        ensureSlugAvailable(request.getSlug(), null);

        String bodyPath = fileUtil.buildBodyPath(type, request.getSlug(), resolveBookSlug(request, type));
        if (fileUtil.markdownExists(bodyPath)) {
            throw new BusinessException(409, "文章文件已存在，拒绝覆盖: " + bodyPath);
        }

        String temporaryPath = null;
        AtomicBoolean promoted = new AtomicBoolean(false);
        try {
            temporaryPath = fileUtil.writeTemporaryMarkdown(bodyPath, body(request));
            registerCreateRollbackCleanup(temporaryPath, bodyPath, promoted);

            Content content = new Content();
            content.setTitle(request.getTitle());
            content.setSlug(request.getSlug());
            content.setBodyPath(bodyPath);
            content.setSummary(request.getSummary());
            content.setType(request.getType());
            content.setStatus(status.name());
            content.setMetadata(request.getMetadata());
            if (status == ContentStatus.PUBLISHED) {
                content.setPublishedAt(LocalDateTime.now());
            }
            contentMapper.insert(content);
            saveAssociations(content.getId(), request.getCategoryIds(), request.getTagIds());

            fileUtil.promoteTemporaryMarkdown(temporaryPath, bodyPath, false);
            promoted.set(true);
            return toDetailVO(content);
        } catch (IOException e) {
            deleteQuietly(temporaryPath);
            if (promoted.get()) {
                deleteQuietly(bodyPath);
            }
            throw new BusinessException(500, "Failed to write markdown file: " + bodyPath, e);
        } catch (RuntimeException e) {
            deleteQuietly(temporaryPath);
            if (promoted.get() && !TransactionSynchronizationManager.isSynchronizationActive()) {
                deleteQuietly(bodyPath);
            }
            throw e;
        }
    }

    @Override
    @Transactional
    public ContentDetailVO update(Long id, ContentSaveRequest request) {
        Content old = contentMapper.findById(id);
        if (old == null) {
            throw new NotFoundException("Content not found: id=" + id);
        }

        ContentType type = parseContentType(request.getType());
        ContentStatus status = parseStatus(request.getStatus(), ContentStatus.valueOf(old.getStatus()));
        validateAssociationIds(request.getCategoryIds(), request.getTagIds());
        ensureSlugAvailable(request.getSlug(), id);

        String newBodyPath = fileUtil.buildBodyPath(type, request.getSlug(), resolveBookSlug(request, type));
        boolean pathChanged = !newBodyPath.equals(old.getBodyPath());
        if (pathChanged && fileUtil.markdownExists(newBodyPath)) {
            throw new BusinessException(409, "文章文件已存在，拒绝覆盖: " + newBodyPath);
        }

        String temporaryPath = null;
        String backupPath = null;
        AtomicBoolean promoted = new AtomicBoolean(false);
        try {
            backupPath = pathChanged ? null : fileUtil.copyToTemporaryMarkdown(old.getBodyPath());
            temporaryPath = fileUtil.writeTemporaryMarkdown(newBodyPath, body(request));
            registerUpdateRollbackCleanup(
                    old.getBodyPath(), newBodyPath, temporaryPath, backupPath, promoted, pathChanged);

            // 更新 DB
            old.setTitle(request.getTitle());
            old.setSlug(request.getSlug());
            old.setBodyPath(newBodyPath);
            old.setSummary(request.getSummary());
            old.setType(request.getType());
            old.setStatus(status.name());
            old.setMetadata(request.getMetadata());

            // 首次发布设 publishedAt
            if (status == ContentStatus.PUBLISHED && old.getPublishedAt() == null) {
                old.setPublishedAt(LocalDateTime.now());
            }
            contentMapper.update(old);

            // 更新关联
            contentCategoryMapper.deleteByContentId(id);
            contentTagMapper.deleteByContentId(id);
            saveAssociations(id, request.getCategoryIds(), request.getTagIds());

            // DB 写入成功后，替换最终文件。
            fileUtil.promoteTemporaryMarkdown(temporaryPath, newBodyPath, true);
            promoted.set(true);
            if (!TransactionSynchronizationManager.isSynchronizationActive() && pathChanged) {
                deleteQuietly(old.getBodyPath());
            }
            return toDetailVO(contentMapper.findById(id));
        } catch (IOException e) {
            cleanupFailedUpdate(
                    old.getBodyPath(), newBodyPath, temporaryPath, backupPath, promoted, pathChanged);
            throw new BusinessException(500, "Failed to write markdown file: " + newBodyPath, e);
        } catch (RuntimeException e) {
            cleanupFailedUpdate(
                    old.getBodyPath(), newBodyPath, temporaryPath, backupPath, promoted, pathChanged);
            throw e;
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Content content = contentMapper.findById(id);
        if (content == null) {
            throw new NotFoundException("Content not found: id=" + id);
        }
        String bodyPath = content.getBodyPath();
        // 删关联
        contentCategoryMapper.deleteByContentId(id);
        contentTagMapper.deleteByContentId(id);
        // 删记录
        contentMapper.delete(id);

        // DB 是事实来源；提交成功后再清理文件，失败只留下可恢复孤儿文件。
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteQuietly(bodyPath);
                }
            });
        } else {
            deleteQuietly(bodyPath);
        }
    }

    // ---- 私有辅助 ----

    private String resolveBookSlug(ContentSaveRequest request, ContentType type) {
        if (type == ContentType.NOVEL) {
            if (request.getCategoryIds() == null || request.getCategoryIds().isEmpty()) {
                throw new BusinessException(400, "NOVEL 内容必须关联一个 NOVEL 分类作为 bookSlug");
            }
            Category category = categoryMapper.findById(request.getCategoryIds().get(0));
            if (category == null || !ContentType.NOVEL.name().equals(category.getType())) {
                throw new BusinessException(400, "NOVEL 内容必须关联有效的 NOVEL 分类");
            }
            return category.getSlug();
        }
        return "";
    }

    private ContentType parseContentType(String value) {
        try {
            return ContentType.valueOf(value);
        } catch (RuntimeException e) {
            throw new BusinessException(400, "非法内容类型: " + value);
        }
    }

    private ContentStatus parseStatus(String value, ContentStatus fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return ContentStatus.valueOf(value);
        } catch (RuntimeException e) {
            throw new BusinessException(400, "非法内容状态: " + value);
        }
    }

    private void ensureSlugAvailable(String slug, Long excludeId) {
        if (contentMapper.countBySlug(slug, excludeId) > 0) {
            throw new BusinessException(409, "文章 slug 已存在: " + slug);
        }
    }

    private void validateAssociationIds(List<Long> categoryIds, List<Long> tagIds) {
        List<Long> distinctCategoryIds = distinctIds(categoryIds);
        if (!distinctCategoryIds.isEmpty()
                && categoryMapper.findByIds(distinctCategoryIds).size() != distinctCategoryIds.size()) {
            throw new BusinessException(400, "包含不存在的分类");
        }
        List<Long> distinctTagIds = distinctIds(tagIds);
        if (!distinctTagIds.isEmpty()
                && tagMapper.findByIds(distinctTagIds).size() != distinctTagIds.size()) {
            throw new BusinessException(400, "包含不存在的标签");
        }
    }

    private List<Long> distinctIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(ids));
    }

    private String body(ContentSaveRequest request) {
        return request.getBody() != null ? request.getBody() : "";
    }

    private void saveAssociations(Long contentId, List<Long> categoryIds, List<Long> tagIds) {
        for (Long categoryId : distinctIds(categoryIds)) {
            contentCategoryMapper.insert(contentId, categoryId);
        }
        for (Long tagId : distinctIds(tagIds)) {
            contentTagMapper.insert(contentId, tagId);
        }
    }

    private void registerCreateRollbackCleanup(String temporaryPath,
                                               String targetPath,
                                               AtomicBoolean promoted) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    deleteQuietly(temporaryPath);
                    if (promoted.get()) {
                        deleteQuietly(targetPath);
                    }
                }
            }
        });
    }

    private void registerUpdateRollbackCleanup(String oldPath,
                                               String newPath,
                                               String temporaryPath,
                                               String backupPath,
                                               AtomicBoolean promoted,
                                               boolean pathChanged) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    deleteQuietly(temporaryPath);
                    if (promoted.get()) {
                        if (pathChanged) {
                            deleteQuietly(newPath);
                        } else {
                            restoreBackup(newPath, backupPath);
                        }
                    }
                    deleteQuietly(backupPath);
                } else if (status == STATUS_COMMITTED) {
                    if (pathChanged) {
                        deleteQuietly(oldPath);
                    }
                    deleteQuietly(backupPath);
                }
            }
        });
    }

    private void cleanupFailedUpdate(String oldPath,
                                     String newPath,
                                     String temporaryPath,
                                     String backupPath,
                                     AtomicBoolean promoted,
                                     boolean pathChanged) {
        deleteQuietly(temporaryPath);
        if (promoted.get()) {
            if (pathChanged) {
                deleteQuietly(newPath);
            } else if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                restoreBackup(newPath, backupPath);
            }
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteQuietly(backupPath);
        }
    }

    private void restoreBackup(String targetPath, String backupPath) {
        if (backupPath == null || !fileUtil.markdownExists(backupPath)) {
            deleteQuietly(targetPath);
            return;
        }
        try {
            fileUtil.promoteTemporaryMarkdown(backupPath, targetPath, true);
        } catch (IOException e) {
            log.error("Failed to restore markdown backup after rollback: {}", targetPath, e);
        }
    }

    private void deleteQuietly(String relativePath) {
        if (relativePath == null) {
            return;
        }
        try {
            fileUtil.deleteMarkdown(relativePath);
        } catch (IOException | RuntimeException e) {
            log.error("Failed to delete orphaned markdown file: {}", relativePath, e);
        }
    }

    private ContentDetailVO toDetailVO(Content c) {
        String body = "";
        try {
            body = fileUtil.readMarkdown(c.getBodyPath());
        } catch (IOException e) {
            body = "";
        }
        return voMapper.toDetailVO(c, body);
    }

    private List<ContentListVO> assembleListVO(List<Content> contents) {
        return voMapper.toListVOs(contents);
    }
}
