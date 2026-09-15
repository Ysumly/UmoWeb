package com.ysumly.umowebbackend.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ImageIntegrityCountsVO {
    private int brokenReferences;
    private int missingFiles;
    private int untrackedFiles;
    private int total;
}
