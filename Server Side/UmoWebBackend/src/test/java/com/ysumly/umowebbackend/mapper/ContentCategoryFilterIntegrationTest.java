package com.ysumly.umowebbackend.mapper;

import com.ysumly.umowebbackend.common.exception.BusinessException;
import com.ysumly.umowebbackend.model.dto.ContentQuery;
import com.ysumly.umowebbackend.model.entity.Content;
import com.ysumly.umowebbackend.service.CategoryHierarchyResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "MYSQL_INTEGRATION", matches = "true")
@Transactional
class ContentCategoryFilterIntegrationTest {

    @Autowired
    private ContentMapper contentMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void descendantFilterReturnsTwoLevelHierarchyInStableOrderWithoutDuplicates() {
        long rootId = insertCategory("root", null);
        long childId = insertCategory("child", rootId);
        long grandchildId = insertCategory("grandchild", childId);
        LocalDateTime sameTime = LocalDateTime.of(2026, 9, 13, 10, 0);
        long rootContentId = insertContent("integration-root", "PUBLISHED", sameTime);
        long childContentId = insertContent("integration-child", "DRAFT", sameTime);
        long grandchildContentId = insertContent("integration-grandchild", "PUBLISHED", sameTime);
        linkContent(rootContentId, rootId);
        linkContent(rootContentId, childId);
        linkContent(childContentId, childId);
        linkContent(grandchildContentId, grandchildId);

        ContentQuery descendantsQuery = query(rootId, true);
        List<Long> resolvedIds = resolver().resolve(descendantsQuery);

        List<Content> descendants =
                contentMapper.findPublished(descendantsQuery, resolvedIds);
        long descendantTotal =
                contentMapper.countPublished(descendantsQuery, resolvedIds);
        List<Content> adminDescendants =
                contentMapper.findAll(descendantsQuery, resolvedIds);
        long adminDescendantTotal =
                contentMapper.countAll(descendantsQuery, resolvedIds);

        assertThat(resolvedIds).containsExactly(rootId, childId, grandchildId);
        assertThat(descendants)
                .extracting(Content::getSlug)
                .containsExactly("integration-grandchild", "integration-root");
        assertThat(descendantTotal).isEqualTo(2);
        assertThat(adminDescendants)
                .extracting(Content::getSlug)
                .containsExactly(
                        "integration-child",
                        "integration-grandchild",
                        "integration-root");
        assertThat(adminDescendantTotal).isEqualTo(3);

        ContentQuery exactQuery = query(rootId, false);
        List<Long> exactIds = resolver().resolve(exactQuery);
        assertThat(contentMapper.findPublished(exactQuery, exactIds))
                .extracting(Content::getSlug)
                .containsExactly("integration-root");
        assertThat(contentMapper.countPublished(exactQuery, exactIds)).isEqualTo(1);

        ContentQuery draftQuery = query(rootId, true);
        draftQuery.setStatus("DRAFT");
        assertThat(contentMapper.findAll(draftQuery, resolvedIds))
                .extracting(Content::getSlug)
                .containsExactly("integration-child");
        assertThat(contentMapper.countAll(draftQuery, resolvedIds)).isEqualTo(1);
    }

    @Test
    void descendantFilterReturnsEmptyResultWhenHierarchyHasNoContent() {
        long rootId = insertCategory("empty-root", null);
        insertCategory("empty-child", rootId);
        ContentQuery query = query(rootId, true);
        List<Long> resolvedIds = resolver().resolve(query);

        assertThat(contentMapper.findPublished(query, resolvedIds)).isEmpty();
        assertThat(contentMapper.countPublished(query, resolvedIds)).isZero();
    }

    @Test
    void adminListGroupsStatusesBeforeApplyingTimeSort() {
        long categoryId = insertCategory("status-order-root", null);
        LocalDateTime older = LocalDateTime.of(2026, 9, 12, 9, 0);
        LocalDateTime newer = LocalDateTime.of(2026, 9, 13, 9, 0);
        List<Long> contentIds = List.of(
                insertContent("integration-archived", "ARCHIVED", newer),
                insertContent("integration-published-old", "PUBLISHED", older),
                insertContent("integration-published-new", "PUBLISHED", newer),
                insertContent("integration-scheduled-new", "SCHEDULED", newer),
                insertContent("integration-draft-old", "DRAFT", older),
                insertContent("integration-draft-new", "DRAFT", newer));
        contentIds.forEach(contentId -> linkContent(contentId, categoryId));

        ContentQuery query = query(categoryId, false);
        query.setSort("published_at_desc");

        assertThat(contentMapper.findAll(query, List.of(categoryId)))
                .extracting(Content::getSlug)
                .containsExactly(
                        "integration-draft-new",
                        "integration-draft-old",
                        "integration-scheduled-new",
                        "integration-published-new",
                        "integration-published-old",
                        "integration-archived");
    }

    @Test
    void reachableCycleIsRejectedByHierarchyResolver() {
        long firstId = insertCategory("cycle-first", null);
        long secondId = insertCategory("cycle-second", firstId);
        long thirdId = insertCategory("cycle-third", secondId);
        jdbcTemplate.update(
                "UPDATE categories SET parent_id = ? WHERE id = ?",
                thirdId,
                firstId);

        ContentQuery query = query(firstId, true);

        assertThatThrownBy(() -> resolver().resolve(query))
                .isInstanceOf(BusinessException.class)
                .hasMessage("分类层级包含循环")
                .extracting("code")
                .isEqualTo(409);
    }

    private CategoryHierarchyResolver resolver() {
        return new CategoryHierarchyResolver(categoryMapper);
    }

    private ContentQuery query(long categoryId, boolean includeDescendants) {
        ContentQuery query = new ContentQuery();
        query.setCategoryId(categoryId);
        query.setIncludeDescendants(includeDescendants);
        query.setPage(1);
        query.setSize(100);
        return query;
    }

    private long insertCategory(String prefix, Long parentId) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.update(
                """
                INSERT INTO categories (name, slug, parent_id, type, sort_order)
                VALUES (?, ?, ?, 'NOTE', 0)
                """,
                prefix,
                prefix + "-" + suffix,
                parentId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE slug = ?",
                Long.class,
                prefix + "-" + suffix);
    }

    private long insertContent(String slug, String status, LocalDateTime publishedAt) {
        jdbcTemplate.update(
                """
                INSERT INTO contents
                    (title, slug, body_path, summary, type, status, published_at)
                VALUES (?, ?, ?, ?, 'NOTE', ?, ?)
                """,
                slug,
                slug,
                "contents/NOTE/" + slug + ".md",
                slug,
                status,
                publishedAt);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM contents WHERE slug = ?",
                Long.class,
                slug);
    }

    private void linkContent(long contentId, long categoryId) {
        jdbcTemplate.update(
                "INSERT INTO content_category (content_id, category_id) VALUES (?, ?)",
                contentId,
                categoryId);
    }
}
