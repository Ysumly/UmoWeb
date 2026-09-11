package com.ysumly.umowebbackend.model.vo;

import lombok.Data;

@Data
public class ImageVO {
    private Long id;
    private String url;
    private String originalName;
    private Long size;
}
