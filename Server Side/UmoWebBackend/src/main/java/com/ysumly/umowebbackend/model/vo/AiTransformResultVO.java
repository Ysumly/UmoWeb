package com.ysumly.umowebbackend.model.vo;

public record AiTransformResultVO(
        String requestId,
        String modeKey,
        int modeVersion,
        String content,
        String model,
        AiTokenUsageVO usage
) {
}
