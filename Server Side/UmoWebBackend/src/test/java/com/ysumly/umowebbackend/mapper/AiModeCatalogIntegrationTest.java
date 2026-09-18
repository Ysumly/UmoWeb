package com.ysumly.umowebbackend.mapper;

import com.ysumly.umowebbackend.common.constant.AiValidationProfile;
import com.ysumly.umowebbackend.common.exception.BusinessException;
import com.ysumly.umowebbackend.model.dto.AiModeUpdateRequest;
import com.ysumly.umowebbackend.model.entity.AiTransformMode;
import com.ysumly.umowebbackend.model.entity.AiTransformModeVersion;
import com.ysumly.umowebbackend.service.impl.admin.AiModeCatalogServiceImpl;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "MYSQL_INTEGRATION", matches = "true")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AiModeCatalogIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AiTransformModeMapper modeMapper;

    @Autowired
    private AiTransformModeVersionMapper versionMapper;

    private final List<Long> insertedModeIds = new ArrayList<>();

    @AfterEach
    void removeTestModes() {
        for (Long modeId : insertedModeIds) {
            jdbcTemplate.update("DELETE FROM ai_transform_modes WHERE id = ?", modeId);
        }
        insertedModeIds.clear();
    }

    @Test
    void schemaContainsFiveDisabledSeedModesAndVersionOne() {
        assertThat(queryInt("SELECT COUNT(*) FROM ai_transform_modes")).isEqualTo(5);
        assertThat(queryInt("SELECT COUNT(*) FROM ai_transform_modes WHERE enabled = 0"))
                .isEqualTo(5);
        assertThat(queryInt(
                "SELECT COUNT(*) FROM ai_transform_mode_versions WHERE version_no = 1"))
                .isEqualTo(5);
    }

    @Test
    void enabledLookupIgnoresDisabledModes() {
        AiTransformMode mode = insertMode(false);

        assertThat(modeMapper.findEnabledByKey(mode.getModeKey())).isNull();
    }

    @Test
    void conditionalVersionUpdateAllowsOnlyOneWriter() {
        AiTransformMode mode = insertMode(false);

        assertThat(modeMapper.updateCurrentVersion(mode.getId(), 1, 2)).isEqualTo(1);
        assertThat(modeMapper.updateCurrentVersion(mode.getId(), 1, 2)).isZero();
        assertThat(modeMapper.findById(mode.getId()).getCurrentVersion()).isEqualTo(2);
    }

    @Test
    void deletingModeCascadesPromptVersions() {
        AiTransformMode mode = insertMode(false);

        assertThat(versionMapper.findVersions(mode.getId())).hasSize(1);
        jdbcTemplate.update("DELETE FROM ai_transform_modes WHERE id = ?", mode.getId());
        insertedModeIds.remove(mode.getId());

        assertThat(versionMapper.findVersions(mode.getId())).isEmpty();
    }

    @Test
    void twoUpdatesWithSameExpectedVersionOnlyFirstSucceeds() {
        AiTransformMode mode = insertMode(false);
        AiModeCatalogServiceImpl service =
                new AiModeCatalogServiceImpl(modeMapper, versionMapper);

        var updated = service.update(mode.getId(), new AiModeUpdateRequest(
                mode.getName(),
                mode.getDescription(),
                "updated-prompt",
                AiValidationProfile.NONE,
                false,
                999,
                1));

        assertThat(updated.currentVersion()).isEqualTo(2);
        assertThatThrownBy(() -> service.update(mode.getId(), new AiModeUpdateRequest(
                mode.getName(),
                mode.getDescription(),
                "racing-prompt",
                AiValidationProfile.NONE,
                false,
                999,
                1)))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(409);
        assertThat(modeMapper.findById(mode.getId()).getCurrentVersion()).isEqualTo(2);
        assertThat(versionMapper.findVersions(mode.getId())).hasSize(2);
    }

    private int queryInt(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private AiTransformMode insertMode(boolean enabled) {
        AiTransformMode mode = new AiTransformMode();
        mode.setModeKey("AI_TEST_" + UUID.randomUUID().toString().replace("-", "").toUpperCase());
        mode.setName("AI test mode");
        mode.setDescription("integration");
        mode.setEnabled(enabled);
        mode.setSortOrder(999);
        mode.setCurrentVersion(1);
        modeMapper.insert(mode);
        insertedModeIds.add(mode.getId());

        AiTransformModeVersion version = new AiTransformModeVersion();
        version.setModeId(mode.getId());
        version.setVersionNo(1);
        version.setSystemPrompt("integration-prompt");
        version.setValidationProfile(AiValidationProfile.NONE);
        versionMapper.insertVersion(version);
        return mode;
    }
}
