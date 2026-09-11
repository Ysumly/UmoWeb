package com.ysumly.umowebbackend.model.vo;

import lombok.Data;

@Data
public class CategoryVO {
    private Long id;
    private String name;
    private String slug;
    private Long parentId;
    private String type;
    private Integer sortOrder;
}
