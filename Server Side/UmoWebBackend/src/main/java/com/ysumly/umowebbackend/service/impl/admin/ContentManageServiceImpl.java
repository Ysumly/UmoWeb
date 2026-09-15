package com.ysumly.umowebbackend.service.impl.admin;

import com.ysumly.umowebbackend.common.constant.BulkContentAction;
import com.ysumly.umowebbackend.common.constant.ContentStatus;
import com.ysumly.umowebbackend.common.constant.ContentType;
import com.ysumly.umowebbackend.common.exception.BulkOperationException;
import com.ysumly.umowebbackend.common.exception.BusinessException;
import com.ysumly.umowebbackend.common.exception.NotFoundException;
import com.ysumly.umowebbackend.common.util.FileUtil;
import com.ysumly.umowebbackend.mapper.*;
import com.ysumly.umowebbackend.model.dto.BulkContentRequest;
import com.ysumly.umowebbackend.model.dto.ContentCategoryLink;
import com.ysumly.umowebbackend.model.dto.ContentQuery;
import com.ysumly.umowebbackend.model.dto.ContentSaveRequest;
import com.ysumly.umowebbackend.model.dto.ContentTagLink;
import com.ysumly.umowebbackend.model.dto.PageResult;
import com.ysumly.umowebbackend.model.entity.Category;
import com.ysumly.umowebbackend.model.entity.Content;
import com.ysumly.umowebbackend.model.vo.BulkContentFailureVO;
import com.ysumly.umowebbackend.model.vo.BulkContentResultVO;
import com.ysumly.umowebbackend.model.vo.ContentDetailVO;
import com.ysumly.umowebbackend.model.vo.ContentListVO;
import com.ysumly.umowebbackend.service.CategoryHierarchyResolver;
import com.ysumly.umowebbackend.service.ContentSearchIndexService;
import com.ysumly.umowebbackend.service.ContentVOMapper;
import com.ysumly.umowebbackend.service.admin.ContentManageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.time.Clock;
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
    private final CategoryHierarchyResolver categoryHierarchyResolver;
    private final ContentSearchIndexService searchIndexService;
    private final Clock clock;

    public ContentManageServiceImpl(ContentMapper contentMapper,
                                    ContentCategoryMapper contentCategoryMapper,
                                    ContentTagMapper contentTagMapper,
                                    CategoryMapper categoryMapper,
                                    TagMapper tagMapper,
                                    FileUtil fileUtil,
                                    ContentVOMapper voMapper,
                                    CategoryHierarchyResolver categoryHierarchyResolver,
                                    ContentSearchIndexService searchIndexService,
                                    Clock clock) {
        this.contentMapper = contentMapper;
        this.contentCategoryMapper = contentCategoryMapper;
        this.contentTagMapper = contentTagMapper;
        this.categoryMapper = categoryMapper;
        this.tagMapper = tagMapper;
        this.fileUtil = fileUtil;
        this.voMapper = voMapper;
        this.categoryHierarchyResolver = categoryHierarchyResolver;
        this.searchIndexService = searchIndexService;
        this.clock = clock;
    }

    @Override
    public PageResult<ContentListVO> list(ContentQuery query) {
        List<Long> categoryIds = categoryHierarchyResolver.resolve(query);
        List<Content> contents = contentMapper.findAll(query, categoryIds);
        long total = contentMapper.countAll(query, categoryIds);
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
        if (status == ContentStatus.ARCHIVED) {
            throw new BusinessException(400, "新建文章不能直接归档");
        }
        validateScheduling(status, request.getScheduledAt(), null, null);
        validateAssociationIds(request.getCategoryIds(), request.getTagIds());
        ensureSlugAvailable(request.getSlug(), null);

        String bodyPath = fileUtil.buildBodyPath(type, request.getSlug(), resolveBookSlug(request, type));
        if (fileUtil.markdownExists(bodyPath)) {
            throw new BusinessException(409, "文章文件已存在，拒绝覆盖: " + bodyPath);
        }

        String temporaryPath = null;
        AtomicBoolean promoted = new AtomicBoolean(false);
        try {
            String markdownBody = body(request);
            temporaryPath = fileUtil.writeTemporaryMarkdown(bodyPath, markdownBody);
            registerCreateRollbackCleanup(temporaryPath, bodyPath, promoted);

            Content content = new Content();
            content.setTitle(request.getTitle());
            content.setSlug(request.getSlug());
            content.setBodyPath(bodyPath);
            content.setSummary(request.getSummary());
            content.setType(request.getType());
            content.setStatus(status.name());
            content.setMetadata(normalizeMetadata(request.getMetadata()));
            content.setScheduledAt(status == ContentStatus.SCHEDULED
                    ? request.getScheduledAt() : null);
            if (status == ContentStatus.PUBLISHED) {
                content.setPublishedAt(now());
            }
            contentMapper.insert(content);
            saveAssociations(content.getId(), request.getCategoryIds(), request.getTagIds());
            searchIndexService.sync(content, markdownBody);

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
        String previousStatus = old.getStatus();
        validateScheduling(status, request.getScheduledAt(), old.getStatus(), old.getPublishedAt());
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
            String markdownBody = body(request);
            temporaryPath = fileUtil.writeTemporaryMarkdown(newBodyPath, markdownBody);
            registerUpdateRollbackCleanup(
                    old.getBodyPath(), newBodyPath, temporaryPath, backupPath, promoted, pathChanged);

            // 更新 DB
            old.setTitle(request.getTitle());
            old.setSlug(request.getSlug());
            old.setBodyPath(newBodyPath);
            old.setSummary(request.getSummary());
            old.setType(request.getType());
            old.setStatus(status.name());
            old.setMetadata(normalizeMetadata(request.getMetadata()));
            old.setScheduledAt(status == ContentStatus.SCHEDULED
                    ? request.getScheduledAt() : null);

            if (status == ContentStatus.PUBLISHED
                    && !ContentStatus.PUBLISHED.name().equals(previousStatus)) {
                old.setPublishedAt(now());
            }
            contentMapper.update(old);

            // 更新关联
            contentCategoryMapper.deleteByContentId(id);
            contentTagMapper.deleteByContentId(id);
            saveAssociations(id, request.getCategoryIds(), request.getTagIds());
            searchIndexService.sync(old, markdownBody);

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
    public BulkContentResultVO bulk(BulkContentRequest request) {
        BulkContentAction action = parseBulkAction(request.getAction());
        List<Long> contentIds = distinctIds(request.getContentIds());
        if (contentIds.isEmpty()) {
            throw new BusinessException(400, "contentIds 不能为空");
        }

        Map<Long, Content> contentsById = new LinkedHashMap<>();
        for (Content content : contentMapper.findByIds(contentIds)) {
            contentsById.put(content.getId(), content);
        }
        List<Long> missingContentIds = contentIds.stream()
                .filter(id -> !contentsById.containsKey(id))
                .toList();
        if (!missingContentIds.isEmpty()) {
            throw new BulkOperationException(
                    404,
                    "批量操作包含不存在的内容",
                    missingContentIds.stream()
                            .map(id -> new BulkContentFailureVO(
                                    id, null, "CONTENT_NOT_FOUND"))
                            .toList());
        }

        return switch (action) {
            case ADD_CATEGORIES -> addLinks(
                    action, contentIds, resolveCategoryIds(request), true);
            case REMOVE_CATEGORIES -> removeCategories(
                    action, contentIds, resolveCategoryIds(request), contentsById);
            case ADD_TAGS -> addLinks(
                    action, contentIds, resolveTagIds(request), false);
            case REMOVE_TAGS -> removeTags(
                    action, contentIds, resolveTagIds(request));
            case ARCHIVE -> archive(action, contentIds, contentsById);
            case RESTORE_DRAFT -> restoreDraft(action, contentIds, contentsById);
        };
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

    private BulkContentAction parseBulkAction(String value) {
        try {
            return BulkContentAction.valueOf(value);
        } catch (RuntimeException e) {
            throw new BusinessException(400, "非法批量操作: " + value);
        }
    }

    private List<Long> resolveCategoryIds(BulkContentRequest request) {
        List<Long> ids = distinctIds(request.getCategoryIds());
        if (ids.isEmpty()) {
            throw new BusinessException(400, "分类批量操作必须选择分类");
        }
        Set<Long> existingIds = categoryMapper.findByIds(ids).stream()
                .map(Category::getId)
                .collect(java.util.stream.Collectors.toSet());
        List<Long> missingIds = ids.stream()
                .filter(id -> !existingIds.contains(id))
                .toList();
        if (!missingIds.isEmpty()) {
            throw new BulkOperationException(
                    404,
                    "批量操作包含不存在的分类",
                    missingIds.stream()
                            .map(id -> new BulkContentFailureVO(
                                    null, id, "CATEGORY_NOT_FOUND"))
                            .toList());
        }
        return ids;
    }

    private List<Long> resolveTagIds(BulkContentRequest request) {
        List<Long> ids = distinctIds(request.getTagIds());
        if (ids.isEmpty()) {
            throw new BusinessException(400, "标签批量操作必须选择标签");
        }
        Set<Long> existingIds = tagMapper.findByIds(ids).stream()
                .map(com.ysumly.umowebbackend.model.entity.Tag::getId)
                .collect(java.util.stream.Collectors.toSet());
        List<Long> missingIds = ids.stream()
                .filter(id -> !existingIds.contains(id))
                .toList();
        if (!missingIds.isEmpty()) {
            throw new BulkOperationException(
                    404,
                    "批量操作包含不存在的标签",
                    missingIds.stream()
                            .map(id -> new BulkContentFailureVO(
                                    null, id, "TAG_NOT_FOUND"))
                            .toList());
        }
        return ids;
    }

    private BulkContentResultVO addLinks(BulkContentAction action,
                                         List<Long> contentIds,
                                         List<Long> targetIds,
                                         boolean category) {
        Map<Long, Set<Long>> existing = category
                ? categoryLinks(contentIds)
                : tagLinks(contentIds);
        Set<Long> changed = new LinkedHashSet<>();
        for (Long contentId : contentIds) {
            Set<Long> current = existing.getOrDefault(contentId, Set.of());
            for (Long targetId : targetIds) {
                if (current.contains(targetId)) {
                    continue;
                }
                changed.add(contentId);
                if (category) {
                    contentCategoryMapper.insertIgnore(contentId, targetId);
                } else {
                    contentTagMapper.insertIgnore(contentId, targetId);
                }
            }
        }
        return bulkResult(action, contentIds.size(), changed.size());
    }

    private BulkContentResultVO removeCategories(BulkContentAction action,
                                                 List<Long> contentIds,
                                                 List<Long> categoryIds,
                                                 Map<Long, Content> contentsById) {
        List<BulkContentFailureVO> failures = contentIds.stream()
                .map(contentsById::get)
                .filter(content -> ContentType.NOVEL.name().equals(content.getType()))
                .map(content -> new BulkContentFailureVO(
                        content.getId(), null, "NOVEL_CATEGORY_REMOVE_REQUIRES_EDIT"))
                .toList();
        if (!failures.isEmpty()) {
            throw new BulkOperationException(
                    409, "小说正文路径依赖目录，请通过编辑页调整分类", failures);
        }

        Map<Long, Set<Long>> existing = categoryLinks(contentIds);
        Set<Long> changed = changedForRemoval(contentIds, categoryIds, existing);
        if (!changed.isEmpty()) {
            contentCategoryMapper.deleteLinks(contentIds, categoryIds);
        }
        return bulkResult(action, contentIds.size(), changed.size());
    }

    private BulkContentResultVO removeTags(BulkContentAction action,
                                           List<Long> contentIds,
                                           List<Long> tagIds) {
        Map<Long, Set<Long>> existing = tagLinks(contentIds);
        Set<Long> changed = changedForRemoval(contentIds, tagIds, existing);
        if (!changed.isEmpty()) {
            contentTagMapper.deleteLinks(contentIds, tagIds);
        }
        return bulkResult(action, contentIds.size(), changed.size());
    }

    private BulkContentResultVO archive(BulkContentAction action,
                                        List<Long> contentIds,
                                        Map<Long, Content> contentsById) {
        List<Content> changed = contentIds.stream()
                .map(contentsById::get)
                .filter(content -> !ContentStatus.ARCHIVED.name().equals(content.getStatus()))
                .toList();
        if (!changed.isEmpty()) {
            contentMapper.archiveByIds(contentIds);
            for (Content content : changed) {
                content.setStatus(ContentStatus.ARCHIVED.name());
                content.setScheduledAt(null);
                searchIndexService.sync(content, null);
            }
        }
        return bulkResult(action, contentIds.size(), changed.size());
    }

    private BulkContentResultVO restoreDraft(BulkContentAction action,
                                             List<Long> contentIds,
                                             Map<Long, Content> contentsById) {
        List<BulkContentFailureVO> failures = contentIds.stream()
                .map(contentsById::get)
                .filter(content -> !ContentStatus.ARCHIVED.name().equals(content.getStatus()))
                .map(content -> new BulkContentFailureVO(
                        content.getId(), null, "NOT_ARCHIVED"))
                .toList();
        if (!failures.isEmpty()) {
            throw new BulkOperationException(
                    409, "只有已归档内容可以恢复为草稿", failures);
        }
        contentMapper.restoreDraftByIds(contentIds);
        for (Long contentId : contentIds) {
            Content content = contentsById.get(contentId);
            content.setStatus(ContentStatus.DRAFT.name());
            content.setScheduledAt(null);
        }
        return bulkResult(action, contentIds.size(), contentIds.size());
    }

    private BulkContentResultVO bulkResult(BulkContentAction action,
                                           int requestedCount,
                                           int updatedCount) {
        return new BulkContentResultVO(
                action.name(),
                requestedCount,
                updatedCount,
                requestedCount - updatedCount);
    }

    private Map<Long, Set<Long>> categoryLinks(List<Long> contentIds) {
        Map<Long, Set<Long>> result = new HashMap<>();
        for (ContentCategoryLink link : contentCategoryMapper.findLinksByContentIds(contentIds)) {
            result.computeIfAbsent(link.getContentId(), ignored -> new LinkedHashSet<>())
                    .add(link.getCategoryId());
        }
        return result;
    }

    private Map<Long, Set<Long>> tagLinks(List<Long> contentIds) {
        Map<Long, Set<Long>> result = new HashMap<>();
        for (ContentTagLink link : contentTagMapper.findLinksByContentIds(contentIds)) {
            result.computeIfAbsent(link.getContentId(), ignored -> new LinkedHashSet<>())
                    .add(link.getTagId());
        }
        return result;
    }

    private Set<Long> changedForRemoval(List<Long> contentIds,
                                        List<Long> targetIds,
                                        Map<Long, Set<Long>> existing) {
        Set<Long> changed = new LinkedHashSet<>();
        Set<Long> targetSet = new HashSet<>(targetIds);
        for (Long contentId : contentIds) {
            if (existing.getOrDefault(contentId, Set.of()).stream()
                    .anyMatch(targetSet::contains)) {
                changed.add(contentId);
            }
        }
        return changed;
    }

    private void validateScheduling(ContentStatus status,
                                    LocalDateTime scheduledAt,
                                    String existingStatus,
                                    LocalDateTime publishedAt) {
        if (status != ContentStatus.SCHEDULED) {
            return;
        }
        if (scheduledAt == null || !scheduledAt.isAfter(now())) {
            throw new BusinessException(400, "计划发布时间必须晚于当前时间");
        }
        boolean firstPublication = publishedAt == null
                && (existingStatus == null
                || ContentStatus.DRAFT.name().equals(existingStatus)
                || ContentStatus.SCHEDULED.name().equals(existingStatus));
        if (!firstPublication) {
            throw new BusinessException(409, "只有未发布的草稿可以设置定时发布");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
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

    private String normalizeMetadata(String metadata) {
        return metadata == null || metadata.isBlank() ? null : metadata;
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
