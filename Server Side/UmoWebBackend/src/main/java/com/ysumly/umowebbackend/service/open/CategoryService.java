package com.ysumly.umowebbackend.service.open;

import com.ysumly.umowebbackend.model.vo.CategoryTreeVO;
import java.util.List;

public interface CategoryService {
    List<CategoryTreeVO> getTree(String type);
}
