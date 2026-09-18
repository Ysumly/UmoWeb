package com.ysumly.umowebbackend.service.impl.admin;

import com.ysumly.umowebbackend.common.exception.BusinessException;
import com.ysumly.umowebbackend.common.exception.NotFoundException;
import com.ysumly.umowebbackend.mapper.AiTransformModeMapper;
import com.ysumly.umowebbackend.mapper.AiTransformModeVersionMapper;
import com.ysumly.umowebbackend.model.dto.AiModeCopyRequest;
import com.ysumly.umowebbackend.model.dto.AiModeCreateRequest;
import com.ysumly.umowebbackend.model.dto.AiModeUpdateRequest;
import com.ysumly.umowebbackend.model.entity.AiTransformMode;
import com.ysumly.umowebbackend.model.entity.AiTransformModeVersion;
import com.ysumly.umowebbackend.model.vo.AiModeSettingsVO;
import com.ysumly.umowebbackend.model.vo.AiModeVersionVO;
import com.ysumly.umowebbackend.service.admin.AiModeCatalogService;
import com.ysumly.umowebbackend.service.admin.AiModeRuntimeConfig;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class AiModeCatalogServiceImpl implements AiModeCatalogService {

    private static final int MAX_RETAINED_VERSIONS = 10;

    private final AiTransformModeMapper modeMapper;
    private final AiTransformModeVersionMapper versionMapper;

    public AiModeCatalogServiceImpl(AiTransformModeMapper modeMapper,
                                    AiTransformModeVersionMapper versionMapper) {
        this.modeMapper = modeMapper;
        this.versionMapper = versionMapper;
    }

    @Override
    public List<AiModeSettingsVO> list() {
        return modeMapper.findAll().stream()
                .map(this::toSettingsVO)
                .toList();
    }

    @Override
    public AiModeSettingsVO getById(Long id) {
        return toSettingsVO(requireMode(id));
    }

    @Override
    @Transactional
    public AiModeSettingsVO create(AiModeCreateRequest request) {
        rejectDuplicateKey(request.modeKey());

        AiTransformMode mode = new AiTransformMode();
        mode.setModeKey(request.modeKey());
        mode.setName(request.name().trim());
        mode.setDescription(trimToEmpty(request.description()));
        mode.setEnabled(Boolean.TRUE.equals(request.enabled()));
        mode.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        mode.setCurrentVersion(1);
        modeMapper.insert(mode);

        AiTransformModeVersion version = new AiTransformModeVersion();
        version.setModeId(mode.getId());
        version.setVersionNo(1);
        version.setSystemPrompt(request.systemPrompt().trim());
        version.setValidationProfile(request.validationProfile());
        versionMapper.insertVersion(version);
        return getById(mode.getId());
    }

    @Override
    @Transactional
    public AiModeSettingsVO copy(Long id, AiModeCopyRequest request) {
        AiTransformMode source = requireMode(id);
        rejectDuplicateKey(request.modeKey());
        AiTransformModeVersion current = requireCurrentVersion(source);

        AiTransformMode copied = new AiTransformMode();
        copied.setModeKey(request.modeKey());
        copied.setName(request.name().trim());
        copied.setDescription(source.getDescription());
        copied.setEnabled(false);
        copied.setSortOrder(0);
        copied.setCurrentVersion(1);
        modeMapper.insert(copied);

        AiTransformModeVersion version = new AiTransformModeVersion();
        version.setModeId(copied.getId());
        version.setVersionNo(1);
        version.setSystemPrompt(current.getSystemPrompt());
        version.setValidationProfile(current.getValidationProfile());
        versionMapper.insertVersion(version);
        return getById(copied.getId());
    }

    @Override
    @Transactional
    public AiModeSettingsVO update(Long id, AiModeUpdateRequest request) {
        AiTransformMode mode = requireMode(id);
        requireExpectedVersion(mode, request.expectedVersion());
        AiTransformModeVersion current = requireCurrentVersion(mode);

        String prompt = request.systemPrompt().trim();
        boolean promptChanged = !current.getSystemPrompt().equals(prompt)
                || current.getValidationProfile() != request.validationProfile();

        mode.setName(request.name().trim());
        mode.setDescription(trimToEmpty(request.description()));
        mode.setEnabled(request.enabled());
        mode.setSortOrder(request.sortOrder());
        mode.setCurrentVersion(request.expectedVersion());
        if (modeMapper.updateMetadata(mode) != 1) {
            throw conflict("模式已在其他窗口更新，请重新加载");
        }

        if (!promptChanged) {
            return toSettingsVO(mode, current);
        }

        int newVersion = request.expectedVersion() + 1;
        AiTransformModeVersion created = new AiTransformModeVersion();
        created.setModeId(id);
        created.setVersionNo(newVersion);
        created.setSystemPrompt(prompt);
        created.setValidationProfile(request.validationProfile());
        versionMapper.insertVersion(created);

        if (modeMapper.updateCurrentVersion(id, request.expectedVersion(), newVersion) != 1) {
            throw conflict("模式已在其他窗口更新，请重新加载");
        }
        pruneVersions(id, newVersion);
        return getById(id);
    }

    @Override
    public List<AiModeVersionVO> listVersions(Long id) {
        requireMode(id);
        return versionMapper.findVersions(id).stream()
                .map(this::toVersionVO)
                .toList();
    }

    @Override
    @Transactional
    public AiModeSettingsVO rollback(Long id, int versionNo, int expectedVersion) {
        AiTransformMode mode = requireMode(id);
        requireExpectedVersion(mode, expectedVersion);
        AiTransformModeVersion target = versionMapper.findVersion(id, versionNo);
        if (target == null) {
            throw new NotFoundException("AI mode version not found: modeId=" + id
                    + ", versionNo=" + versionNo);
        }

        int newVersion = expectedVersion + 1;
        if (modeMapper.updateCurrentVersion(id, expectedVersion, newVersion) != 1) {
            throw conflict("模式已在其他窗口更新，请重新加载");
        }

        AiTransformModeVersion created = new AiTransformModeVersion();
        created.setModeId(id);
        created.setVersionNo(newVersion);
        created.setSystemPrompt(target.getSystemPrompt());
        created.setValidationProfile(target.getValidationProfile());
        versionMapper.insertVersion(created);

        pruneVersions(id, newVersion);
        return getById(id);
    }

    @Override
    public Optional<AiModeRuntimeConfig> findEnabledByKey(String modeKey) {
        AiTransformMode mode = modeMapper.findEnabledByKey(modeKey);
        if (mode == null) {
            return Optional.empty();
        }
        AiTransformModeVersion version = requireCurrentVersion(mode);
        return Optional.of(new AiModeRuntimeConfig(
                mode.getId(),
                mode.getModeKey(),
                mode.getName(),
                version.getVersionNo(),
                version.getSystemPrompt(),
                version.getValidationProfile()));
    }

    private AiTransformMode requireMode(Long id) {
        AiTransformMode mode = modeMapper.findById(id);
        if (mode == null) {
            throw new NotFoundException("AI mode not found: id=" + id);
        }
        return mode;
    }

    private AiTransformModeVersion requireCurrentVersion(AiTransformMode mode) {
        AiTransformModeVersion version = versionMapper.findCurrentVersion(mode.getId());
        if (version == null) {
            throw new IllegalStateException("AI mode current version is missing: id="
                    + mode.getId());
        }
        return version;
    }

    private void rejectDuplicateKey(String modeKey) {
        if (modeMapper.findByKey(modeKey) != null) {
            throw conflict("AI mode key already exists: " + modeKey);
        }
    }

    private void requireExpectedVersion(AiTransformMode mode, int expectedVersion) {
        if (mode.getCurrentVersion() == null || mode.getCurrentVersion() != expectedVersion) {
            throw conflict("模式已在其他窗口更新，请重新加载");
        }
    }

    private void pruneVersions(Long modeId, int currentVersion) {
        if (currentVersion > MAX_RETAINED_VERSIONS) {
            versionMapper.deleteVersionsBefore(
                    modeId,
                    currentVersion - MAX_RETAINED_VERSIONS + 1);
        }
    }

    private AiModeSettingsVO toSettingsVO(AiTransformMode mode) {
        return toSettingsVO(mode, requireCurrentVersion(mode));
    }

    private AiModeSettingsVO toSettingsVO(AiTransformMode mode,
                                          AiTransformModeVersion version) {
        return new AiModeSettingsVO(
                mode.getId(),
                mode.getModeKey(),
                mode.getName(),
                mode.getDescription(),
                Boolean.TRUE.equals(mode.getEnabled()),
                mode.getSortOrder() == null ? 0 : mode.getSortOrder(),
                mode.getCurrentVersion() == null ? version.getVersionNo()
                        : mode.getCurrentVersion(),
                version.getSystemPrompt(),
                version.getValidationProfile(),
                mode.getCreatedAt(),
                mode.getUpdatedAt());
    }

    private AiModeVersionVO toVersionVO(AiTransformModeVersion version) {
        return new AiModeVersionVO(
                version.getVersionNo(),
                version.getSystemPrompt(),
                version.getValidationProfile(),
                version.getCreatedAt());
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private BusinessException conflict(String message) {
        return new BusinessException(409, message);
    }
}
