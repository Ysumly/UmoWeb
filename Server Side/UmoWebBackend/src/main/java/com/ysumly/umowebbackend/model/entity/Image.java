package com.ysumly.umowebbackend.model.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Image {
    private Long id;
    private String originalName;
    private String storedName;
    private String path;
    private Long size;
    private String contentType;
    private Integer width;
    private Integer height;
    private LocalDateTime createdAt;
}
