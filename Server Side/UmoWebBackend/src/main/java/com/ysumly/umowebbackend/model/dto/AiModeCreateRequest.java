package com.ysumly.umowebbackend.model.dto;

import com.ysumly.umowebbackend.common.constant.AiValidationProfile;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AiModeCreateRequest(
        @NotBlank
        @Pattern(regexp = "[A-Z][A-Z0-9_]{2,63}")
        String modeKey,
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
        Boolean enabled,
        Integer sortOrder
) {
}
