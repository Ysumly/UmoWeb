package com.ysumly.umowebbackend.model.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ContentDetailVO extends ContentListVO {
    private String body;
}
