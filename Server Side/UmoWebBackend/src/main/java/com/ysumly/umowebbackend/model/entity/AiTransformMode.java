package com.ysumly.umowebbackend.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiTransformMode {
    private Long id;
    private String modeKey;
    private String name;
    private String description;
    private Boolean enabled;
    private Integer sortOrder;
    private Integer currentVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
