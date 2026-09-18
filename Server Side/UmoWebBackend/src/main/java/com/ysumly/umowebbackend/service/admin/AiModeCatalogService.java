package com.ysumly.umowebbackend.service.admin;

import com.ysumly.umowebbackend.model.dto.AiModeCopyRequest;
import com.ysumly.umowebbackend.model.dto.AiModeCreateRequest;
import com.ysumly.umowebbackend.model.dto.AiModeUpdateRequest;
import com.ysumly.umowebbackend.model.vo.AiModeSettingsVO;
import com.ysumly.umowebbackend.model.vo.AiModeVersionVO;

import java.util.List;
import java.util.Optional;

public interface AiModeCatalogService {
    List<AiModeSettingsVO> list();
    AiModeSettingsVO getById(Long id);
    AiModeSettingsVO create(AiModeCreateRequest request);
    AiModeSettingsVO copy(Long id, AiModeCopyRequest request);
    AiModeSettingsVO update(Long id, AiModeUpdateRequest request);
    List<AiModeVersionVO> listVersions(Long id);
    AiModeSettingsVO rollback(Long id, int versionNo, int expectedVersion);
    Optional<AiModeRuntimeConfig> findByKey(String modeKey);
    Optional<AiModeRuntimeConfig> findEnabledByKey(String modeKey);
}
