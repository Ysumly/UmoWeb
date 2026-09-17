package com.ysumly.umowebbackend.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BulkContentFailureVO {
    private Long contentId;
    private Long targetId;
    private String reason;
}
