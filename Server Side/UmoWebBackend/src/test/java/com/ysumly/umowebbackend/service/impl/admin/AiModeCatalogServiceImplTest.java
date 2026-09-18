package com.ysumly.umowebbackend.service.impl.admin;

import com.ysumly.umowebbackend.common.constant.AiValidationProfile;
import com.ysumly.umowebbackend.common.exception.BusinessException;
import com.ysumly.umowebbackend.common.exception.NotFoundException;
import com.ysumly.umowebbackend.mapper.AiTransformModeMapper;
import com.ysumly.umowebbackend.mapper.AiTransformModeVersionMapper;
import com.ysumly.umowebbackend.model.dto.AiModeCopyRequest;
import com.ysumly.umowebbackend.model.dto.AiModeCreateRequest;
import com.ysumly.umowebbackend.model.dto.AiModeUpdateRequest;
import com.ysumly.umowebbackend.model.entity.AiTransformMode;
import com.ysumly.umowebbackend.model.entity.AiTransformModeVersion;
import com.ysumly.umowebbackend.service.admin.AiModeRuntimeConfig;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiModeCatalogServiceImplTest {

    private final AiTransformModeMapper modeMapper = mock(AiTransformModeMapper.class);
    private final AiTransformModeVersionMapper versionMapper =
            mock(AiTransformModeVersionMapper.class);
    private final AiModeCatalogServiceImpl service =
            new AiModeCatalogServiceImpl(modeMapper, versionMapper);

    @Test
    void createTrimsFieldsAndStoresVersionOne() {
        AiModeCreateRequest request = new AiModeCreateRequest(
                "CUSTOM_MODE",
                "  自定义模式  ",
                "  说明  ",
                "  prompt-v1  ",
                AiValidationProfile.NONE,
                null,
                null);
        doAnswer(invocation -> {
            AiTransformMode mode = invocation.getArgument(0);
            mode.setId(42L);
            return 1;
        }).when(modeMapper).insert(any(AiTransformMode.class));
        AiTransformMode persisted = mode(42L, "CUSTOM_MODE", false, 1);
        persisted.setName("自定义模式");
        persisted.setDescription("说明");
        persisted.setCreatedAt(LocalDateTime.of(2026, 9, 18, 12, 0));
        persisted.setUpdatedAt(LocalDateTime.of(2026, 9, 18, 12, 0));
        when(modeMapper.findById(42L)).thenReturn(persisted);
        when(versionMapper.findCurrentVersion(42L)).thenReturn(version(
                42L, 1, "prompt-v1", AiValidationProfile.NONE));

        var result = service.create(request);

        assertThat(result.id()).isEqualTo(42L);
        assertThat(result.name()).isEqualTo("自定义模式");
        assertThat(result.description()).isEqualTo("说明");
        assertThat(result.systemPrompt()).isEqualTo("prompt-v1");
        assertThat(result.enabled()).isFalse();
        assertThat(result.currentVersion()).isEqualTo(1);
        assertThat(result.createdAt()).isEqualTo(LocalDateTime.of(2026, 9, 18, 12, 0));
        verify(versionMapper).insertVersion(any(AiTransformModeVersion.class));
    }

    @Test
    void createRejectsDuplicateModeKey() {
        when(modeMapper.findByKey("CUSTOM_MODE")).thenReturn(new AiTransformMode());

        assertThatThrownBy(() -> service.create(new AiModeCreateRequest(
                "CUSTOM_MODE",
                "自定义模式",
                "",
                "prompt-v1",
                AiValidationProfile.NONE,
                false,
                0)))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(409);
        verify(modeMapper, never()).insert(any(AiTransformMode.class));
    }

    @Test
    void copyUsesCurrentPromptAndStartsDisabled() {
        AiTransformMode source = mode(7L, "SOURCE_MODE", true, 3);
        when(modeMapper.findById(7L)).thenReturn(source);
        when(versionMapper.findCurrentVersion(7L)).thenReturn(version(
                7L, 3, "source-prompt", AiValidationProfile.TRANSLATION));
        doAnswer(invocation -> {
            AiTransformMode mode = invocation.getArgument(0);
            mode.setId(8L);
            return 1;
        }).when(modeMapper).insert(any(AiTransformMode.class));
        AiTransformMode copied = mode(8L, "COPIED_MODE", false, 1);
        copied.setName("复制模式");
        copied.setDescription("Description");
        copied.setCreatedAt(LocalDateTime.of(2026, 9, 18, 12, 0));
        copied.setUpdatedAt(LocalDateTime.of(2026, 9, 18, 12, 0));
        when(modeMapper.findById(8L)).thenReturn(copied);
        when(versionMapper.findCurrentVersion(8L)).thenReturn(version(
                8L, 1, "source-prompt", AiValidationProfile.TRANSLATION));

        var result = service.copy(
                7L,
                new AiModeCopyRequest("COPIED_MODE", "复制模式"));

        assertThat(result.modeKey()).isEqualTo("COPIED_MODE");
        assertThat(result.name()).isEqualTo("复制模式");
        assertThat(result.enabled()).isFalse();
        assertThat(result.currentVersion()).isEqualTo(1);
        assertThat(result.systemPrompt()).isEqualTo("source-prompt");
        assertThat(result.validationProfile()).isEqualTo(AiValidationProfile.TRANSLATION);
    }

    @Test
    void metadataOnlyUpdateDoesNotCreateVersion() {
        AiTransformMode mode = mode(1L, "CUSTOM_MODE", false, 2);
        mode.setUpdatedAt(LocalDateTime.of(2026, 9, 18, 12, 0));
        when(modeMapper.findById(1L)).thenReturn(mode);
        when(versionMapper.findCurrentVersion(1L)).thenReturn(version(
                1L, 2, "same-prompt", AiValidationProfile.NONE));
        when(modeMapper.updateMetadata(any(AiTransformMode.class))).thenAnswer(invocation -> {
            mode.setUpdatedAt(LocalDateTime.of(2026, 9, 18, 12, 1));
            return 1;
        });

        var result = service.update(1L, new AiModeUpdateRequest(
                "新名称",
                "新说明",
                "same-prompt",
                AiValidationProfile.NONE,
                true,
                4,
                2));

        assertThat(result.currentVersion()).isEqualTo(2);
        assertThat(result.enabled()).isTrue();
        assertThat(result.sortOrder()).isEqualTo(4);
        assertThat(result.updatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 18, 12, 1));
        verify(versionMapper, never()).insertVersion(any(AiTransformModeVersion.class));
        verify(modeMapper, never()).updateCurrentVersion(anyLong(), anyInt(), anyInt());
    }

    @Test
    void promptUpdateCreatesNextVersionAndPrunesOldHistory() {
        AiTransformMode mode = mode(1L, "CUSTOM_MODE", true, 11);
        AiTransformModeVersion oldVersion = version(
                1L, 11, "old-prompt", AiValidationProfile.NONE);
        AiTransformModeVersion newVersion = version(
                1L, 12, "new-prompt", AiValidationProfile.NONE);
        when(modeMapper.findById(1L)).thenReturn(mode);
        when(versionMapper.findCurrentVersion(1L)).thenReturn(oldVersion, newVersion);
        when(modeMapper.updateMetadata(any(AiTransformMode.class))).thenReturn(1);
        when(modeMapper.updateCurrentVersion(1L, 11, 12)).thenAnswer(invocation -> {
            mode.setCurrentVersion(12);
            return 1;
        });

        var result = service.update(1L, new AiModeUpdateRequest(
                "自定义模式",
                "",
                "new-prompt",
                AiValidationProfile.NONE,
                true,
                1,
                11));

        assertThat(result.currentVersion()).isEqualTo(12);
        verify(versionMapper).insertVersion(any(AiTransformModeVersion.class));
        verify(versionMapper).deleteVersionsBefore(1L, 3);
    }

    @Test
    void staleExpectedVersionReturnsConflict() {
        AiTransformMode mode = mode(1L, "CUSTOM_MODE", false, 4);
        when(modeMapper.findById(1L)).thenReturn(mode);

        assertThatThrownBy(() -> service.update(1L, new AiModeUpdateRequest(
                "名称",
                "",
                "prompt",
                AiValidationProfile.NONE,
                false,
                0,
                3)))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(409);
    }

    @Test
    void rollbackCopiesHistoricalVersionAsNewVersion() {
        AiTransformMode mode = mode(1L, "CUSTOM_MODE", true, 3);
        when(modeMapper.findById(1L)).thenReturn(mode);
        when(versionMapper.findVersion(1L, 1)).thenReturn(version(
                1L, 1, "historical-prompt", AiValidationProfile.EXACT_CONTENT));
        when(modeMapper.updateCurrentVersion(1L, 3, 4)).thenAnswer(invocation -> {
            mode.setCurrentVersion(4);
            return 1;
        });
        when(versionMapper.findCurrentVersion(1L)).thenReturn(version(
                1L, 4, "historical-prompt", AiValidationProfile.EXACT_CONTENT));

        var result = service.rollback(1L, 1, 3);

        assertThat(result.currentVersion()).isEqualTo(4);
        assertThat(result.systemPrompt()).isEqualTo("historical-prompt");
        verify(versionMapper).insertVersion(any(AiTransformModeVersion.class));
        verify(versionMapper, never()).deleteVersionsBefore(anyLong(), anyInt());
    }

    @Test
    void rollbackRejectsUnknownHistoricalVersion() {
        when(modeMapper.findById(1L)).thenReturn(mode(1L, "CUSTOM_MODE", true, 2));
        when(versionMapper.findVersion(1L, 99)).thenReturn(null);

        assertThatThrownBy(() -> service.rollback(1L, 99, 2))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void runtimeLookupReturnsEnabledCurrentVersion() {
        AiTransformMode mode = mode(1L, "CUSTOM_MODE", true, 2);
        when(modeMapper.findEnabledByKey("CUSTOM_MODE")).thenReturn(mode);
        when(versionMapper.findCurrentVersion(1L)).thenReturn(version(
                1L, 2, "runtime-prompt", AiValidationProfile.TRANSLATION));

        Optional<AiModeRuntimeConfig> result = service.findEnabledByKey("CUSTOM_MODE");

        assertThat(result).contains(new AiModeRuntimeConfig(
                1L,
                "CUSTOM_MODE",
                "Custom Mode",
                2,
                "runtime-prompt",
                AiValidationProfile.TRANSLATION));
    }

    @Test
    void runtimeLookupIgnoresDisabledMode() {
        when(modeMapper.findEnabledByKey("CUSTOM_MODE")).thenReturn(null);

        assertThat(service.findEnabledByKey("CUSTOM_MODE")).isEmpty();
        verify(versionMapper, never()).findCurrentVersion(anyLong());
    }

    @Test
    void keyLookupReportsDisabledModeWithoutHidingIt() {
        AiTransformMode mode = mode(1L, "CUSTOM_MODE", false, 2);
        when(modeMapper.findByKey("CUSTOM_MODE")).thenReturn(mode);
        when(versionMapper.findCurrentVersion(1L)).thenReturn(version(
                1L, 2, "runtime-prompt", AiValidationProfile.NONE));

        Optional<AiModeRuntimeConfig> result = service.findByKey("CUSTOM_MODE");

        assertThat(result).contains(new AiModeRuntimeConfig(
                1L,
                "CUSTOM_MODE",
                "Custom Mode",
                2,
                "runtime-prompt",
                AiValidationProfile.NONE,
                false));
    }

    @Test
    void versionListingRequiresExistingModeAndKeepsMapperOrder() {
        when(modeMapper.findById(1L)).thenReturn(mode(1L, "CUSTOM_MODE", true, 2));
        when(versionMapper.findVersions(1L)).thenReturn(List.of(
                version(1L, 2, "prompt-v2", AiValidationProfile.NONE),
                version(1L, 1, "prompt-v1", AiValidationProfile.NONE)));

        var result = service.listVersions(1L);

        assertThat(result).extracting("versionNo").containsExactly(2, 1);
    }

    private AiTransformMode mode(Long id, String modeKey, boolean enabled, int currentVersion) {
        AiTransformMode mode = new AiTransformMode();
        mode.setId(id);
        mode.setModeKey(modeKey);
        mode.setName("Custom Mode");
        mode.setDescription("Description");
        mode.setEnabled(enabled);
        mode.setSortOrder(1);
        mode.setCurrentVersion(currentVersion);
        return mode;
    }

    private AiTransformModeVersion version(Long modeId,
                                           int versionNo,
                                           String prompt,
                                           AiValidationProfile profile) {
        AiTransformModeVersion version = new AiTransformModeVersion();
        version.setModeId(modeId);
        version.setVersionNo(versionNo);
        version.setSystemPrompt(prompt);
        version.setValidationProfile(profile);
        return version;
    }
}
