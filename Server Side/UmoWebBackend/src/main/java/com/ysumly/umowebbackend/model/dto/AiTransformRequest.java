package com.ysumly.umowebbackend.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiTransformRequest(
        @NotBlank
        String modeKey,

        @NotBlank
        @Size(max = 20_000)
        String content
) {
}
