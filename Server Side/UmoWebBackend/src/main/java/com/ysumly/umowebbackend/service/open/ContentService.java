package com.ysumly.umowebbackend.service.open;

import com.ysumly.umowebbackend.model.dto.ContentQuery;
import com.ysumly.umowebbackend.model.dto.PageResult;
import com.ysumly.umowebbackend.model.vo.ContentDetailVO;
import com.ysumly.umowebbackend.model.vo.ContentListVO;

public interface ContentService {
    PageResult<ContentListVO> listPublished(ContentQuery query);
    ContentDetailVO getBySlug(String slug);
    PageResult<ContentListVO> search(ContentQuery query);
}
