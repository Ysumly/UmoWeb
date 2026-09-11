package com.ysumly.umowebbackend.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OptionSaveRequest {
    @NotBlank(message = "值不能为空")
    private String value;
}
