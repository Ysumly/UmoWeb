package com.ysumly.umowebbackend.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AiModeCopyRequest(
        @NotBlank
        @Pattern(regexp = "[A-Z][A-Z0-9_]{2,63}")
        String modeKey,
        @NotBlank
        @Size(max = 100)
        String name
) {
}
