package com.ysumly.umowebbackend.service.admin;

import com.ysumly.umowebbackend.model.dto.CategorySaveRequest;
import com.ysumly.umowebbackend.model.vo.CategoryTreeVO;
import com.ysumly.umowebbackend.model.vo.CategoryVO;
import java.util.List;

public interface CategoryManageService {
    List<CategoryTreeVO> getTree(String type);
    CategoryVO getById(Long id);
    CategoryVO create(CategorySaveRequest request);
    CategoryVO update(Long id, CategorySaveRequest request);
    void delete(Long id);
}
