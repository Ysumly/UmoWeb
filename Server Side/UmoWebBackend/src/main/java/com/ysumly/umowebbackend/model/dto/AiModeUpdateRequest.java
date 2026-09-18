package com.ysumly.umowebbackend.model.dto;

import com.ysumly.umowebbackend.common.constant.AiValidationProfile;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AiModeUpdateRequest(
        @NotBlank
        @Size(max = 100)
        String name,
        @Size(max = 500)
        String description,
        @NotBlank
        @Size(max = 20_000)
        String systemPrompt,
        @NotNull
        AiValidationProfile validationProfile,
        boolean enabled,
        int sortOrder,
        int expectedVersion
) {
}
