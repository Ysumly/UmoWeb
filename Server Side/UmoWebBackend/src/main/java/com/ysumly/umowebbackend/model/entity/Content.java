package com.ysumly.umowebbackend.model.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Content {
    private Long id;
    private String title;
    private String slug;
    private String bodyPath;
    private String summary;
    private String type;
    private String status;
    private String metadata;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime publishedAt;
}
