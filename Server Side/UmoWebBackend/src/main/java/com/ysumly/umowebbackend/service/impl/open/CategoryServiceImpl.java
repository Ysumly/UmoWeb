package com.ysumly.umowebbackend.service.impl.open;

import com.ysumly.umowebbackend.mapper.CategoryMapper;
import com.ysumly.umowebbackend.model.entity.Category;
import com.ysumly.umowebbackend.model.vo.CategoryTreeVO;
import com.ysumly.umowebbackend.service.open.CategoryService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;

    public CategoryServiceImpl(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    @Override
    public List<CategoryTreeVO> getTree(String type) {
        List<Category> all;
        if (type != null && !type.isBlank()) {
            all = categoryMapper.findByType(type);
        } else {
            // 公开端取全部类型，管理端也可复用 — 这里简化：取全部
            all = categoryMapper.findAll();
        }
        return buildTree(all);
    }

    // ---- 供 admin 复用 ----

    public static List<CategoryTreeVO> buildTree(List<Category> all) {
        // parentId → children
        Map<Long, List<Category>> childrenMap = all.stream()
                .filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(Category::getParentId));

        // 顶级节点
        List<Category> roots = all.stream()
                .filter(c -> c.getParentId() == null)
                .toList();

        List<CategoryTreeVO> tree = new ArrayList<>();
        for (Category root : roots) {
            tree.add(toTreeVO(root, childrenMap));
        }
        return tree;
    }

    private static CategoryTreeVO toTreeVO(Category c, Map<Long, List<Category>> childrenMap) {
        CategoryTreeVO vo = new CategoryTreeVO();
        vo.setId(c.getId());
        vo.setName(c.getName());
        vo.setSlug(c.getSlug());
        vo.setType(c.getType());

        List<Category> children = childrenMap.get(c.getId());
        if (children != null) {
            vo.setChildren(children.stream()
                    .map(child -> toTreeVO(child, childrenMap))
                    .toList());
        } else {
            vo.setChildren(List.of());
        }
        return vo;
    }
}
