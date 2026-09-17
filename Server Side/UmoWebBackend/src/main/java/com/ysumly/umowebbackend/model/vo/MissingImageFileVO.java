package com.ysumly.umowebbackend.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MissingImageFileVO {
    private Long id;
    private String url;
    private String originalName;
}
