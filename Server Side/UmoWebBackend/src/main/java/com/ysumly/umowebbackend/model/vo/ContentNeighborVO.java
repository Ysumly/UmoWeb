package com.ysumly.umowebbackend.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ContentNeighborVO {
    private Long id;
    private String title;
    private String slug;
    private LocalDateTime publishedAt;
}
