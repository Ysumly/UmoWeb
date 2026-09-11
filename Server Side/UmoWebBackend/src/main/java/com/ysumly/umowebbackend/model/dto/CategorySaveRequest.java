package com.ysumly.umowebbackend.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CategorySaveRequest {
    @NotBlank(message = "分类名不能为空")
    @Size(max = 100, message = "分类名长度不能超过 100")
    private String name;

    @NotBlank(message = "slug 不能为空")
    @Size(max = 100, message = "slug 长度不能超过 100")
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{0,99}",
            message = "slug 只能包含字母、数字、下划线和连字符")
    private String slug;

    private Long parentId;

    @NotBlank(message = "分类类型不能为空")
    @Pattern(regexp = "NOTE|NOVEL|BOOK_REVIEW", message = "type 必须是 NOTE、NOVEL 或 BOOK_REVIEW")
    private String type;

    private Integer sortOrder;
}
