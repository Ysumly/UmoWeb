package com.ysumly.umowebbackend.model.dto;

import com.ysumly.umowebbackend.common.exception.BusinessException;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ContentQuery {
    @NotNull(message = "page 不能为空")
    @Min(value = 1, message = "page 必须大于等于 1")
    @Max(value = 1_000_000, message = "page 不能超过 1000000")
    private Integer page = 1;

    @NotNull(message = "size 不能为空")
    @Min(value = 1, message = "size 必须大于等于 1")
    @Max(value = 100, message = "size 不能超过 100")
    private Integer size = 10;

    @Pattern(regexp = "NOTE|NOVEL|BOOK_REVIEW", message = "type 必须是 NOTE、NOVEL 或 BOOK_REVIEW")
    private String type;

    private Long categoryId;
    private Boolean includeDescendants = false;
    private Long tagId;

    @Pattern(regexp = "DRAFT|SCHEDULED|PUBLISHED|ARCHIVED",
            message = "status 必须是 DRAFT、SCHEDULED、PUBLISHED 或 ARCHIVED")
    private String status;

    private String sort = "published_at_desc";

    @Size(max = 200, message = "q 长度不能超过 200")
    private String q;

    public int getOffset() {
        if (page == null || size == null) {
            return 0;
        }
        long offset = ((long) page - 1) * size;
        if (offset > Integer.MAX_VALUE) {
            throw new BusinessException(400, "page 过大");
        }
        return (int) offset;
    }

    @AssertTrue(message = "includeDescendants 需要同时提供 categoryId")
    public boolean isDescendantFilterValid() {
        return !Boolean.TRUE.equals(includeDescendants) || categoryId != null;
    }
}
