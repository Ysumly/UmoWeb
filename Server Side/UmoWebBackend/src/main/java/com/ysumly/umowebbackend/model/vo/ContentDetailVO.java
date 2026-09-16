package com.ysumly.umowebbackend.model.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ContentDetailVO extends ContentListVO {
    private String body;
    private ContentNeighborVO previous;
    private ContentNeighborVO next;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<ContentListVO> related;
}
