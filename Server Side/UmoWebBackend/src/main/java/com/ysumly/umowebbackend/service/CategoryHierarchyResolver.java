package com.ysumly.umowebbackend.service;

import com.ysumly.umowebbackend.common.exception.BusinessException;
import com.ysumly.umowebbackend.mapper.CategoryMapper;
import com.ysumly.umowebbackend.model.dto.ContentQuery;
import com.ysumly.umowebbackend.model.entity.Category;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class CategoryHierarchyResolver {

    static final int MAX_DEPTH = 32;

    private final CategoryMapper categoryMapper;

    public CategoryHierarchyResolver(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    public List<Long> resolve(ContentQuery query) {
        Long categoryId = query.getCategoryId();
        if (categoryId == null) {
            return null;
        }
        if (!Boolean.TRUE.equals(query.getIncludeDescendants())) {
            return List.of(categoryId);
        }

        Map<Long, Category> categoriesById = new HashMap<>();
        Map<Long, List<Long>> childIdsByParent = new HashMap<>();
        for (Category category : categoryMapper.findAll()) {
            categoriesById.put(category.getId(), category);
            if (category.getParentId() != null) {
                childIdsByParent
                        .computeIfAbsent(category.getParentId(), ignored -> new ArrayList<>())
                        .add(category.getId());
            }
        }
        if (!categoriesById.containsKey(categoryId)) {
            return List.of(categoryId);
        }

        List<Long> resolved = new ArrayList<>();
        collectDescendants(
                categoryId,
                0,
                childIdsByParent,
                new HashSet<>(),
                resolved);
        return resolved;
    }

    private void collectDescendants(Long categoryId,
                                    int depth,
                                    Map<Long, List<Long>> childIdsByParent,
                                    Set<Long> path,
                                    List<Long> resolved) {
        if (depth > MAX_DEPTH) {
            throw new BusinessException(409, "分类层级超过 32 层");
        }
        if (!path.add(categoryId)) {
            throw new BusinessException(409, "分类层级包含循环");
        }

        resolved.add(categoryId);
        for (Long childId : childIdsByParent.getOrDefault(categoryId, List.of())) {
            collectDescendants(childId, depth + 1, childIdsByParent, path, resolved);
        }
        path.remove(categoryId);
    }
}
