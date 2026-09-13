package com.ysumly.umowebbackend.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ImageCleanupTask {
    private Long id;
    private Long imageId;
    private String path;
    private Integer attempts;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
