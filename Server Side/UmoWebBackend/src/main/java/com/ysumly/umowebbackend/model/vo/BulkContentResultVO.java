package com.ysumly.umowebbackend.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BulkContentResultVO {
    private String action;
    private int requestedCount;
    private int updatedCount;
    private int unchangedCount;
}
