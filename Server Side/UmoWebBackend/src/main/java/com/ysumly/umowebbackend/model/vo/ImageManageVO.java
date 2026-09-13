package com.ysumly.umowebbackend.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ImageManageVO {
    private Long id;
    private String url;
    private String originalName;
    private Long size;
    private String contentType;
    private LocalDateTime createdAt;
    private boolean referenced;
}
