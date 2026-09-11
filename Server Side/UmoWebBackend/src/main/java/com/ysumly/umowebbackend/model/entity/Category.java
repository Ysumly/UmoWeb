package com.ysumly.umowebbackend.model.entity;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class Category {
    private Long id;
    private String name;
    private String slug;
    private Long parentId;
    private String type;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 非 DB 字段 — 查询时组装子节点
    private List<Category> children;
}
