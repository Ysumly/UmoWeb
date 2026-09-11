package com.ysumly.umowebbackend.model.dto;

import com.ysumly.umowebbackend.common.validation.ValidJsonObject;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class ContentSaveRequest {
    @NotBlank(message = "标题不能为空")
    @Size(max = 500, message = "标题长度不能超过 500")
    private String title;

    @NotBlank(message = "slug 不能为空")
    @Size(max = 200, message = "slug 长度不能超过 200")
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{0,199}",
            message = "slug 只能包含字母、数字、下划线和连字符")
    private String slug;

    private String body;

    @Size(max = 2000, message = "摘要长度不能超过 2000")
    private String summary;

    @NotBlank(message = "类型不能为空")
    @Pattern(regexp = "NOTE|NOVEL|BOOK_REVIEW", message = "type 必须是 NOTE、NOVEL 或 BOOK_REVIEW")
    private String type;

    @Pattern(regexp = "DRAFT|PUBLISHED", message = "status 必须是 DRAFT 或 PUBLISHED")
    private String status;

    private List<Long> categoryIds;
    private List<Long> tagIds;

    @ValidJsonObject
    private String metadata;
}
