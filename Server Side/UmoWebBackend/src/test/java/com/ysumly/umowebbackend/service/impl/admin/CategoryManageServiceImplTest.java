package com.ysumly.umowebbackend.service.impl.admin;

import com.ysumly.umowebbackend.common.exception.BusinessException;
import com.ysumly.umowebbackend.mapper.CategoryMapper;
import com.ysumly.umowebbackend.mapper.ContentCategoryMapper;
import com.ysumly.umowebbackend.model.dto.CategorySaveRequest;
import com.ysumly.umowebbackend.model.entity.Category;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CategoryManageServiceImplTest {

    private final CategoryMapper categoryMapper = mock(CategoryMapper.class);
    private final ContentCategoryMapper contentCategoryMapper = mock(ContentCategoryMapper.class);
    private final CategoryManageServiceImpl service =
            new CategoryManageServiceImpl(categoryMapper, contentCategoryMapper);

    @Test
    void deleteRejectsCategoryUsedByContent() {
        when(categoryMapper.findById(1L)).thenReturn(category());
        when(contentCategoryMapper.countContentsByCategoryId(1L)).thenReturn(3L);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(409);
        verify(categoryMapper, never()).delete(anyLong());
    }

    @Test
    void deleteRejectsCategoryWithChildren() {
        when(categoryMapper.findById(1L)).thenReturn(category());
        when(categoryMapper.countChildren(1L)).thenReturn(2L);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(409);
        verify(categoryMapper, never()).delete(anyLong());
    }

    @Test
    void deleteRemovesLeafCategoryWithoutAssociations() {
        when(categoryMapper.findById(1L)).thenReturn(category());
        when(contentCategoryMapper.countContentsByCategoryId(1L)).thenReturn(0L);
        when(categoryMapper.countChildren(1L)).thenReturn(0L);

        service.delete(1L);

        verify(categoryMapper).delete(1L);
    }

    @Test
    void updateRejectsSelfParent() {
        when(categoryMapper.findById(1L)).thenReturn(category());
        CategorySaveRequest request = categoryRequest();
        request.setParentId(1L);

        assertThatThrownBy(() -> service.update(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(400);
        verify(categoryMapper, never()).update(any());
    }

    @Test
    void updateRejectsParentCycle() {
        Category first = category();
        Category second = category();
        second.setId(2L);
        second.setParentId(1L);
        when(categoryMapper.findById(1L)).thenReturn(first);
        when(categoryMapper.findById(2L)).thenReturn(second);
        CategorySaveRequest request = categoryRequest();
        request.setParentId(2L);

        assertThatThrownBy(() -> service.update(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(400);
        verify(categoryMapper, never()).update(any());
    }

    @Test
    void createRejectsMissingParent() {
        CategorySaveRequest request = categoryRequest();
        request.setParentId(99L);
        when(categoryMapper.findById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(400);
        verify(categoryMapper, never()).insert(any());
    }

    private Category category() {
        Category category = new Category();
        category.setId(1L);
        category.setName("Java");
        category.setSlug("java");
        category.setType("NOTE");
        return category;
    }

    private CategorySaveRequest categoryRequest() {
        CategorySaveRequest request = new CategorySaveRequest();
        request.setName("Java");
        request.setSlug("java");
        request.setType("NOTE");
        request.setSortOrder(0);
        return request;
    }
}
