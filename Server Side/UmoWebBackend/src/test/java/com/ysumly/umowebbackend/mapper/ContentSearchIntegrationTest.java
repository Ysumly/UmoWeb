package com.ysumly.umowebbackend.mapper;

import com.ysumly.umowebbackend.model.entity.Content;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "MYSQL_INTEGRATION", matches = "true")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ContentSearchIntegrationTest {

    @Autowired
    private ContentSearchMapper contentSearchMapper;

    @Autowired
    private ContentMapper contentMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> insertedContentIds = new ArrayList<>();

    @AfterEach
    void removeTestContent() {
        for (Long contentId : insertedContentIds) {
            jdbcTemplate.update("DELETE FROM contents WHERE id = ?", contentId);
        }
        insertedContentIds.clear();
    }

    @Test
    void repeatedUpsertKeepsOneChineseSearchableBody() {
        long contentId = insertContent("PUBLISHED");

        contentSearchMapper.upsert(contentId, "第一版正文");
        contentSearchMapper.upsert(contentId, "海内存知己，天涯若比邻。");

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM content_search WHERE content_id = ?",
                Integer.class,
                contentId);
        Integer matches = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM content_search
                WHERE content_id = ?
                  AND MATCH(body_text) AGAINST(? IN NATURAL LANGUAGE MODE)
                """,
                Integer.class,
                contentId,
                "天涯若比邻");

        assertThat(rows).isEqualTo(1);
        assertThat(matches).isEqualTo(1);
    }

    @Test
    void staleDraftIndexIsRemovedAndForeignDeleteCascades() {
        long publishedId = insertContent("PUBLISHED");
        long draftId = insertContent("DRAFT");
        contentSearchMapper.upsert(publishedId, "公开正文");
        contentSearchMapper.upsert(draftId, "草稿正文");

        contentSearchMapper.deleteNotPublished();

        assertThat(indexCount(publishedId)).isEqualTo(1);
        assertThat(indexCount(draftId)).isZero();

        jdbcTemplate.update("DELETE FROM contents WHERE id = ?", publishedId);

        assertThat(indexCount(publishedId)).isZero();
    }

    @Test
    void searchMatchesTitleSummaryAndBodyButNeverDraft() {
        long titleId = insertContent("甲丙标题词", "普通摘要", "PUBLISHED");
        long summaryId = insertContent("普通标题", "乙丁摘要词", "PUBLISHED");
        long bodyId = insertContent("普通标题二", "普通摘要二", "PUBLISHED");
        long draftId = insertContent("草稿标题", "草稿摘要", "DRAFT");
        contentSearchMapper.upsert(bodyId, "戊己正文词只存在于已发布正文");
        contentSearchMapper.upsert(draftId, "戊己正文词只存在于草稿正文");

        assertThat(searchIds("甲丙标题词")).containsExactly(titleId);
        assertThat(searchIds("乙丁摘要词")).containsExactly(summaryId);
        assertThat(searchIds("戊己正文词")).containsExactly(bodyId);
        assertThat(searchIds("")).contains(titleId, summaryId, bodyId).doesNotContain(draftId);
    }

    private long insertContent(String status) {
        return insertContent("搜索集成测试", "用于搜索集成测试", status);
    }

    private long insertContent(String title, String summary, String status) {
        String slug = "search-integration-" + UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO contents
                    (title, slug, body_path, summary, type, status, published_at)
                VALUES (?, ?, ?, ?, 'NOTE', ?, NOW())
                """,
                title,
                slug,
                "contents/NOTE/" + slug + ".md",
                summary,
                status);
        Long contentId = jdbcTemplate.queryForObject(
                "SELECT id FROM contents WHERE slug = ?",
                Long.class,
                slug);
        insertedContentIds.add(contentId);
        return contentId;
    }

    private java.util.List<Long> searchIds(String query) {
        return contentMapper.search(query, 0, 100).stream()
                .map(Content::getId)
                .toList();
    }

    private int indexCount(long contentId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM content_search WHERE content_id = ?",
                Integer.class,
                contentId);
    }
}
