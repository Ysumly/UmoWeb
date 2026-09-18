package com.ysumly.umowebbackend.service.admin;

import com.ysumly.umowebbackend.common.constant.AiValidationProfile;

public record AiModeRuntimeConfig(
        Long modeId,
        String modeKey,
        String name,
        int versionNo,
        String systemPrompt,
        AiValidationProfile validationProfile,
        boolean enabled
) {
    public AiModeRuntimeConfig(
            Long modeId,
            String modeKey,
            String name,
            int versionNo,
            String systemPrompt,
            AiValidationProfile validationProfile) {
        this(modeId, modeKey, name, versionNo, systemPrompt, validationProfile, true);
    }
}
