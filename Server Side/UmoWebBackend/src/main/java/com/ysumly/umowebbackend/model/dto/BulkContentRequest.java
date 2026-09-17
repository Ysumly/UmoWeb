package com.ysumly.umowebbackend.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BulkContentRequest {

    @NotBlank(message = "action 不能为空")
    private String action;

    @NotEmpty(message = "contentIds 不能为空")
    @Size(max = 100, message = "contentIds 不能超过 100 个")
    private List<@NotNull(message = "contentId 不能为空") Long> contentIds;

    @Size(max = 100, message = "categoryIds 不能超过 100 个")
    private List<@NotNull(message = "categoryId 不能为空") Long> categoryIds;

    @Size(max = 100, message = "tagIds 不能超过 100 个")
    private List<@NotNull(message = "tagId 不能为空") Long> tagIds;
}
