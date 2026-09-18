package com.ysumly.umowebbackend.model.vo;

import com.ysumly.umowebbackend.common.constant.AiValidationProfile;

import java.time.LocalDateTime;

public record AiModeSettingsVO(
        Long id,
        String modeKey,
        String name,
        String description,
        boolean enabled,
        int sortOrder,
        int currentVersion,
        String systemPrompt,
        AiValidationProfile validationProfile,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
