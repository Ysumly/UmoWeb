package com.ysumly.umowebbackend.model.vo;

import com.ysumly.umowebbackend.common.constant.AiValidationProfile;

import java.time.LocalDateTime;

public record AiModeVersionVO(
        int versionNo,
        String systemPrompt,
        AiValidationProfile validationProfile,
        LocalDateTime createdAt
) {
}
