package com.ysumly.umowebbackend.model.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class ContentListVO {
    private Long id;
    private String title;
    private String slug;
    private String summary;
    private String excerpt;
    private String type;
    private String status;
    private List<CategoryTreeVO> categories;
    private List<TagVO> tags;
    private Map<String, Object> metadata;
    private LocalDateTime publishedAt;
    private LocalDateTime scheduledAt;
}
