package com.ysumly.umowebbackend.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BrokenImageReferenceVO {
    private String url;
    private String sourceType;
    private Long sourceId;
    private String sourceLabel;
}
