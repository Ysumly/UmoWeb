package com.ysumly.umowebbackend.service.impl.admin;

import com.ysumly.umowebbackend.common.exception.BusinessException;
import com.ysumly.umowebbackend.common.exception.NotFoundException;
import com.ysumly.umowebbackend.mapper.CategoryMapper;
import com.ysumly.umowebbackend.mapper.ContentCategoryMapper;
import com.ysumly.umowebbackend.model.dto.CategorySaveRequest;
import com.ysumly.umowebbackend.model.entity.Category;
import com.ysumly.umowebbackend.model.vo.CategoryTreeVO;
import com.ysumly.umowebbackend.model.vo.CategoryVO;
import com.ysumly.umowebbackend.service.admin.CategoryManageService;
import com.ysumly.umowebbackend.service.impl.open.CategoryServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryManageServiceImpl implements CategoryManageService {

    private final CategoryMapper categoryMapper;
    private final ContentCategoryMapper contentCategoryMapper;

    public CategoryManageServiceImpl(CategoryMapper categoryMapper,
                                     ContentCategoryMapper contentCategoryMapper) {
        this.categoryMapper = categoryMapper;
        this.contentCategoryMapper = contentCategoryMapper;
    }

    @Override
    public List<CategoryTreeVO> getTree(String type) {
        List<Category> all;
        if (type != null && !type.isBlank()) {
            all = categoryMapper.findByType(type);
        } else {
            all = categoryMapper.findAll();
        }
        return CategoryServiceImpl.buildTree(all);
    }

    @Override
    public CategoryVO getById(Long id) {
        Category cat = categoryMapper.findById(id);
        if (cat == null) {
            throw new NotFoundException("Category not found: id=" + id);
        }
        return toVO(cat);
    }

    @Override
    public CategoryVO create(CategorySaveRequest request) {
        Category cat = new Category();
        cat.setName(request.getName());
        cat.setSlug(request.getSlug());
        cat.setParentId(request.getParentId());
        cat.setType(request.getType());
        cat.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        categoryMapper.insert(cat);
        return toVO(cat);
    }

    @Override
    public CategoryVO update(Long id, CategorySaveRequest request) {
        Category cat = categoryMapper.findById(id);
        if (cat == null) {
            throw new NotFoundException("Category not found: id=" + id);
        }
        cat.setName(request.getName());
        cat.setSlug(request.getSlug());
        cat.setParentId(request.getParentId());
        cat.setType(request.getType());
        cat.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        categoryMapper.update(cat);
        return toVO(cat);
    }

    @Override
    public void delete(Long id) {
        if (categoryMapper.findById(id) == null) {
            throw new NotFoundException("Category not found: id=" + id);
        }
        long contentCount = contentCategoryMapper.countContentsByCategoryId(id);
        if (contentCount > 0) {
            throw new BusinessException(409,
                    "Cannot delete category: it is associated with " + contentCount + " content(s)");
        }
        long childCount = categoryMapper.countChildren(id);
        if (childCount > 0) {
            throw new BusinessException(409,
                    "Cannot delete category: it has " + childCount + " child category(ies)");
        }
        categoryMapper.delete(id);
    }

    private CategoryVO toVO(Category category) {
        CategoryVO vo = new CategoryVO();
        vo.setId(category.getId());
        vo.setName(category.getName());
        vo.setSlug(category.getSlug());
        vo.setParentId(category.getParentId());
        vo.setType(category.getType());
        vo.setSortOrder(category.getSortOrder());
        return vo;
    }
}
