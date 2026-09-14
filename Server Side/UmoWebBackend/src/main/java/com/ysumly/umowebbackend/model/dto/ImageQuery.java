package com.ysumly.umowebbackend.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ImageQuery {

    @NotNull(message = "page 不能为空")
    @Min(value = 1, message = "page 必须大于等于 1")
    @Max(value = 1_000_000, message = "page 不能超过 1000000")
    private Integer page = 1;

    @NotNull(message = "size 不能为空")
    @Min(value = 1, message = "size 必须大于等于 1")
    @Max(value = 100, message = "size 不能超过 100")
    private Integer size = 24;

    @Pattern(regexp = "REFERENCED|ORPHANED",
            message = "usage 必须是 REFERENCED 或 ORPHANED")
    private String usage;
}
