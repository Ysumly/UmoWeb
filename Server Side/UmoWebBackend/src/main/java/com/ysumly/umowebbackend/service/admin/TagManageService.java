package com.ysumly.umowebbackend.service.admin;

import com.ysumly.umowebbackend.model.dto.TagSaveRequest;
import com.ysumly.umowebbackend.model.vo.TagVO;
import java.util.List;

public interface TagManageService {
    List<TagVO> getAll();
    TagVO create(TagSaveRequest request);
    TagVO update(Long id, TagSaveRequest request);
    void delete(Long id);
}
