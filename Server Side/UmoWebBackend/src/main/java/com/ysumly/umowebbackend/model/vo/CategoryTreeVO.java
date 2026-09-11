package com.ysumly.umowebbackend.model.vo;

import lombok.Data;
import java.util.List;

@Data
public class CategoryTreeVO {
    private Long id;
    private String name;
    private String slug;
    private String type;
    private List<CategoryTreeVO> children;
}
