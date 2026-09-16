package com.ysumly.umowebbackend.mapper;

import com.ysumly.umowebbackend.model.entity.Content;
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

@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "MYSQL_INTEGRATION", matches = "true")
@Transactional
class RelatedContentIntegrationTest {

    @Autowired
    private ContentMapper contentMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void ranksRelatedPublishedContentsAndExcludesDraftsNeighborsAndUnrelatedItems() {
        long sharedTagOne = insertTag("shared-tag-one");
        long sharedTagTwo = insertTag("shared-tag-two");
        long sharedCategoryOne = insertCategory("shared-category-one");
        long sharedCategoryTwo = insertCategory("shared-category-two");

        long currentId = insertContent(
                "related-current",
                "NOTE",
                "PUBLISHED",
                LocalDateTime.of(2026, 9, 10, 10, 0));
        linkTags(currentId, sharedTagOne, sharedTagTwo);
        linkCategories(currentId, sharedCategoryOne, sharedCategoryTwo);

        long scoreNineId = insertContent(
                "related-score-nine",
                "NOTE",
                "PUBLISHED",
                LocalDateTime.of(2026, 9, 9, 10, 0));
        linkTags(scoreNineId, sharedTagOne, sharedTagTwo);
        linkCategories(scoreNineId, sharedCategoryOne);

        long scoreEightId = insertContent(
                "related-score-eight",
                "NOTE",
                "PUBLISHED",
                LocalDateTime.of(2026, 9, 8, 10, 0));
        linkTags(scoreEightId, sharedTagOne);
        linkCategories(scoreEightId, sharedCategoryOne, sharedCategoryTwo);

        long scoreFourOlderId = insertContent(
                "related-score-four-older",
                "NOTE",
                "PUBLISHED",
                LocalDateTime.of(2026, 9, 7, 10, 0));
        linkTags(scoreFourOlderId, sharedTagTwo);

        long scoreFourNewerId = insertContent(
                "related-score-four-newer",
                "NOTE",
                "PUBLISHED",
                LocalDateTime.of(2026, 9, 8, 10, 0));
        linkTags(scoreFourNewerId, sharedTagTwo);

        long sameTypeOnlyId = insertContent(
                "related-same-type",
                "NOTE",
                "PUBLISHED",
                LocalDateTime.of(2026, 9, 6, 10, 0));
        long unrelatedId = insertContent(
                "related-unrelated",
                "BOOK_REVIEW",
                "PUBLISHED",
                LocalDateTime.of(2026, 9, 11, 10, 0));
        long draftId = insertContent(
                "related-draft",
                "NOTE",
                "DRAFT",
                LocalDateTime.of(2026, 9, 12, 10, 0));
        linkTags(draftId, sharedTagOne, sharedTagTwo);
        linkCategories(draftId, sharedCategoryOne, sharedCategoryTwo);
        long excludedNeighborId = insertContent(
                "related-neighbor",
                "NOTE",
                "PUBLISHED",
                LocalDateTime.of(2026, 9, 13, 10, 0));
        linkTags(excludedNeighborId, sharedTagOne, sharedTagTwo);
        linkCategories(excludedNeighborId, sharedCategoryOne, sharedCategoryTwo);

        List<Content> related = contentMapper.findRelatedPublished(
                currentId,
                "NOTE",
                List.of(excludedNeighborId),
                4);

        assertThat(related).hasSize(4);
        assertThat(related.get(0).getSlug()).startsWith("related-score-nine-");
        assertThat(related.get(1).getSlug()).startsWith("related-score-eight-");
        assertThat(related.get(2).getSlug()).startsWith("related-score-four-newer-");
        assertThat(related.get(3).getSlug()).startsWith("related-score-four-older-");
        assertThat(related)
                .extracting(Content::getId)
                .doesNotContain(currentId, draftId, excludedNeighborId, unrelatedId, sameTypeOnlyId);
        assertThat(related.get(0).getRelationScore()).isEqualTo(9);
        assertThat(related.get(1).getRelationScore()).isEqualTo(8);
        assertThat(related.get(2).getRelationScore()).isEqualTo(4);
        assertThat(related.get(3).getRelationScore()).isEqualTo(4);
    }

    @Test
    void sameTypeCandidatesUseOnePointAndSameTimeUsesIdDescending() {
        LocalDateTime publishedAt = LocalDateTime.of(2026, 9, 10, 10, 0);
        long currentId = insertContent(
                "same-type-current",
                "NOTE",
                "PUBLISHED",
                publishedAt);
        long lowerId = insertContent(
                "same-type-lower",
                "NOTE",
                "PUBLISHED",
                publishedAt);
        long higherId = insertContent(
                "same-type-higher",
                "NOTE",
                "PUBLISHED",
                publishedAt);

        List<Content> related = contentMapper.findRelatedPublished(
                currentId,
                "NOTE",
                List.of(),
                2);

        assertThat(related)
                .extracting(Content::getId)
                .containsExactly(higherId, lowerId);
        assertThat(related)
                .extracting(Content::getRelationScore)
                .containsExactly(1, 1);
    }

    private long insertTag(String prefix) {
        String slug = prefix + "-" + UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO tags (name, slug) VALUES (?, ?)",
                prefix,
                slug);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tags WHERE slug = ?",
                Long.class,
                slug);
    }

    private long insertCategory(String prefix) {
        String slug = prefix + "-" + UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO categories (name, slug, type, sort_order)
                VALUES (?, ?, 'NOTE', 0)
                """,
                prefix,
                slug);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE slug = ?",
                Long.class,
                slug);
    }

    private long insertContent(String slug, String type, String status, LocalDateTime publishedAt) {
        String uniqueSlug = slug + "-" + UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO contents
                    (title, slug, body_path, summary, type, status, published_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                slug,
                uniqueSlug,
                "contents/" + type + "/" + uniqueSlug + ".md",
                "related integration test",
                type,
                status,
                publishedAt);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM contents WHERE slug = ?",
                Long.class,
                uniqueSlug);
    }

    private void linkTags(long contentId, long... tagIds) {
        for (long tagId : tagIds) {
            jdbcTemplate.update(
                    "INSERT INTO content_tag (content_id, tag_id) VALUES (?, ?)",
                    contentId,
                    tagId);
        }
    }

    private void linkCategories(long contentId, long... categoryIds) {
        for (long categoryId : categoryIds) {
            jdbcTemplate.update(
                    "INSERT INTO content_category (content_id, category_id) VALUES (?, ?)",
                    contentId,
                    categoryId);
        }
    }
}
