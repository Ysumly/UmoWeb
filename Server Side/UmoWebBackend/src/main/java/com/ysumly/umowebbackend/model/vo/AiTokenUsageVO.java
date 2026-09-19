package com.ysumly.umowebbackend.model.vo;

public record AiTokenUsageVO(
        int inputTokens,
        int outputTokens,
        int totalTokens
) {
}
