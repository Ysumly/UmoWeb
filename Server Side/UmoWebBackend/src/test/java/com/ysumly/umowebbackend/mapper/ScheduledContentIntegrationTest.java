package com.ysumly.umowebbackend.mapper;

import com.ysumly.umowebbackend.model.dto.ContentQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "MYSQL_INTEGRATION", matches = "true")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ScheduledContentIntegrationTest {

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
    void dueContentIsHiddenUntilAtomicallyPublishedAtItsScheduledTime() {
        LocalDateTime scheduledAt = LocalDateTime.of(2026, 9, 15, 20, 0);
        long contentId = insertScheduledContent(scheduledAt);
        ContentQuery query = new ContentQuery();
        query.setPage(1);
        query.setSize(100);

        assertThat(contentMapper.findPublished(query, List.of()))
                .extracting(content -> content.getId())
                .doesNotContain(contentId);
        assertThat(contentMapper.findDueScheduledIds(scheduledAt.plusSeconds(30), 100))
                .contains(contentId);

        assertThat(contentMapper.publishScheduled(contentId, scheduledAt)).isEqualTo(1);
        assertThat(contentMapper.publishScheduled(contentId, scheduledAt)).isZero();

        Integer statusRows = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM contents
                WHERE id = ?
                  AND status = 'PUBLISHED'
                  AND published_at = ?
                  AND scheduled_at IS NULL
                """,
                Integer.class,
                contentId,
                scheduledAt);
        assertThat(statusRows).isEqualTo(1);
        assertThat(contentMapper.findPublished(query, List.of()))
                .extracting(content -> content.getId())
                .contains(contentId);
    }

    private long insertScheduledContent(LocalDateTime scheduledAt) {
        String slug = "scheduled-integration-" + UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO contents
                    (title, slug, body_path, summary, type, status, scheduled_at)
                VALUES (?, ?, ?, ?, 'NOTE', 'SCHEDULED', ?)
                """,
                "计划发布集成测试",
                slug,
                "contents/NOTE/" + slug + ".md",
                "计划发布集成测试",
                scheduledAt);
        Long contentId = jdbcTemplate.queryForObject(
                "SELECT id FROM contents WHERE slug = ?",
                Long.class,
                slug);
        insertedContentIds.add(contentId);
        return contentId;
    }
}
