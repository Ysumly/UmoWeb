package com.ysumly.umowebbackend.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
public class ImageIntegrityReportVO {
    private LocalDateTime scannedAt;
    private ImageIntegrityCountsVO counts;
    private List<BrokenImageReferenceVO> brokenReferences;
    private List<MissingImageFileVO> missingFiles;
    private List<UntrackedImageFileVO> untrackedFiles;
}
