package com.ysumly.umowebbackend.model.dto;

import jakarta.validation.constraints.NotBlank;

public record AiTransformRequest(
        @NotBlank
        String modeKey,

        @NotBlank
        String content
) {
}
