package com.ysumly.umowebbackend.model.entity;

import com.ysumly.umowebbackend.common.constant.AiValidationProfile;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiTransformModeVersion {
    private Long id;
    private Long modeId;
    private Integer versionNo;
    private String systemPrompt;
    private AiValidationProfile validationProfile;
    private LocalDateTime createdAt;
}
