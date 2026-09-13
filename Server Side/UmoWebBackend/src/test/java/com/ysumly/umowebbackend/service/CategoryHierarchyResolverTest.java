package com.ysumly.umowebbackend.service;

import com.ysumly.umowebbackend.common.exception.BusinessException;
import com.ysumly.umowebbackend.mapper.CategoryMapper;
import com.ysumly.umowebbackend.model.dto.ContentQuery;
import com.ysumly.umowebbackend.model.entity.Category;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CategoryHierarchyResolverTest {

    private final CategoryMapper categoryMapper = mock(CategoryMapper.class);
    private final CategoryHierarchyResolver resolver =
            new CategoryHierarchyResolver(categoryMapper);

    @Test
    void exactCategoryFilterReturnsOnlySelectedIdWithoutLoadingHierarchy() {
        ContentQuery query = new ContentQuery();
        query.setCategoryId(1L);

        assertThat(resolver.resolve(query)).containsExactly(1L);
        verify(categoryMapper, never()).findAll();
    }

    @Test
    void descendantFilterReturnsRootChildAndGrandchild() {
        when(categoryMapper.findAll()).thenReturn(List.of(
                category(1L, null),
                category(2L, 1L),
                category(3L, 2L),
                category(9L, null)));
        ContentQuery query = new ContentQuery();
        query.setCategoryId(1L);
        query.setIncludeDescendants(true);

        assertThat(resolver.resolve(query)).containsExactly(1L, 2L, 3L);
    }

    @Test
    void missingSelectedCategoryReturnsOriginalIdForEmptyQueryResult() {
        when(categoryMapper.findAll()).thenReturn(List.of(category(2L, null)));
        ContentQuery query = new ContentQuery();
        query.setCategoryId(99L);
        query.setIncludeDescendants(true);

        assertThat(resolver.resolve(query)).containsExactly(99L);
    }

    @Test
    void noCategoryFilterReturnsNoResolvedIds() {
        assertThat(resolver.resolve(new ContentQuery())).isNull();
    }

    @Test
    void reachableCategoryCycleIsRejected() {
        when(categoryMapper.findAll()).thenReturn(List.of(
                category(1L, 3L),
                category(2L, 1L),
                category(3L, 2L)));
        ContentQuery query = new ContentQuery();
        query.setCategoryId(1L);
        query.setIncludeDescendants(true);

        assertThatThrownBy(() -> resolver.resolve(query))
                .isInstanceOf(BusinessException.class)
                .hasMessage("分类层级包含循环")
                .extracting("code")
                .isEqualTo(409);
    }

    @Test
    void hierarchyDeeperThanThirtyTwoLevelsIsRejected() {
        List<Category> categories = new ArrayList<>();
        for (long id = 1; id <= 34; id++) {
            categories.add(category(id, id == 1 ? null : id - 1));
        }
        when(categoryMapper.findAll()).thenReturn(categories);
        ContentQuery query = new ContentQuery();
        query.setCategoryId(1L);
        query.setIncludeDescendants(true);

        assertThatThrownBy(() -> resolver.resolve(query))
                .isInstanceOf(BusinessException.class)
                .hasMessage("分类层级超过 32 层")
                .extracting("code")
                .isEqualTo(409);
    }

    private Category category(long id, Long parentId) {
        Category category = new Category();
        category.setId(id);
        category.setParentId(parentId);
        return category;
    }
}
