package com.ysumly.umowebbackend.service.ai;

public class AiProviderException extends RuntimeException {

    private final AiProviderErrorCategory category;
    private final Integer upstreamStatus;
    private final String upstreamRequestId;

    public AiProviderException(AiProviderErrorCategory category) {
        this(category, null, null);
    }

    public AiProviderException(AiProviderErrorCategory category,
                               Integer upstreamStatus,
                               String upstreamRequestId) {
        super(message(category, upstreamStatus, upstreamRequestId));
        this.category = category;
        this.upstreamStatus = upstreamStatus;
        this.upstreamRequestId = upstreamRequestId;
    }

    public AiProviderErrorCategory getCategory() {
        return category;
    }

    public Integer getUpstreamStatus() {
        return upstreamStatus;
    }

    public String getUpstreamRequestId() {
        return upstreamRequestId;
    }

    private static String message(AiProviderErrorCategory category,
                                  Integer upstreamStatus,
                                  String upstreamRequestId) {
        StringBuilder message = new StringBuilder("AI provider error: ").append(category);
        if (upstreamStatus != null) {
            message.append(", status=").append(upstreamStatus);
        }
        if (upstreamRequestId != null && !upstreamRequestId.isBlank()) {
            message.append(", upstreamRequestId=").append(upstreamRequestId);
        }
        return message.toString();
    }
}
