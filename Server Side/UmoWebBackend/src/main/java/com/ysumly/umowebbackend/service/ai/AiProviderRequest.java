package com.ysumly.umowebbackend.service.ai;

import java.time.Duration;

public record AiProviderRequest(
        String model,
        String systemPrompt,
        String userContent,
        int maxOutputChars,
        Duration timeout
) {
}
