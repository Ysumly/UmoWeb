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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        validateParent(null, request.getParentId());
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
        validateParent(id, request.getParentId());
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

    private void validateParent(Long categoryId, Long parentId) {
        if (parentId == null) {
            return;
        }
        Set<Long> visited = new HashSet<>();
        Long currentId = parentId;
        while (currentId != null) {
            if (currentId.equals(categoryId) || !visited.add(currentId)) {
                throw new BusinessException(400, "分类父级不能形成循环");
            }
            Category parent = categoryMapper.findById(currentId);
            if (parent == null) {
                throw new BusinessException(400, "父分类不存在: id=" + currentId);
            }
            currentId = parent.getParentId();
        }
    }
}
