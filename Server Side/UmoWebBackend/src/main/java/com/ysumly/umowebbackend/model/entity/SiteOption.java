package com.ysumly.umowebbackend.model.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SiteOption {
    private Long id;
    private String optionKey;
    private String optionValue;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
