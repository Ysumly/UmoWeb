package com.ysumly.umowebbackend.model.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Tag {
    private Long id;
    private String name;
    private String slug;
    private LocalDateTime createdAt;
}
