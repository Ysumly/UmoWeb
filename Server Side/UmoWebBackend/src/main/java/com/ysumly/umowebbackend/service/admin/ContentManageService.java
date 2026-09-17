package com.ysumly.umowebbackend.service.admin;

import com.ysumly.umowebbackend.model.dto.ContentQuery;
import com.ysumly.umowebbackend.model.dto.ContentSaveRequest;
import com.ysumly.umowebbackend.model.dto.BulkContentRequest;
import com.ysumly.umowebbackend.model.dto.PageResult;
import com.ysumly.umowebbackend.model.vo.BulkContentResultVO;
import com.ysumly.umowebbackend.model.vo.ContentDetailVO;
import com.ysumly.umowebbackend.model.vo.ContentListVO;

public interface ContentManageService {
    PageResult<ContentListVO> list(ContentQuery query);
    ContentDetailVO getById(Long id);
    ContentDetailVO create(ContentSaveRequest request);
    ContentDetailVO update(Long id, ContentSaveRequest request);
    BulkContentResultVO bulk(BulkContentRequest request);
    void delete(Long id);
}
