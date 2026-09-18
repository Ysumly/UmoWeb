package com.ysumly.umowebbackend.model.vo;

public record AiSettingsVO(
        boolean enabled,
        String provider,
        String model,
        int maxInputChars,
        int maxOutputChars,
        int modeCount
) {
}
