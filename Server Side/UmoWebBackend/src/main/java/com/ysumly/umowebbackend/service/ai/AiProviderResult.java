package com.ysumly.umowebbackend.service.ai;

public record AiProviderResult(
        String content,
        String finishReason,
        int inputTokens,
        int outputTokens,
        int totalTokens,
        String upstreamRequestId
) {
}
