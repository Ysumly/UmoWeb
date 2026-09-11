package com.ysumly.umowebbackend.service.impl.admin;

import com.ysumly.umowebbackend.common.exception.BusinessException;
import com.ysumly.umowebbackend.mapper.CategoryMapper;
import com.ysumly.umowebbackend.mapper.ContentCategoryMapper;
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

    private Category category() {
        Category category = new Category();
        category.setId(1L);
        category.setName("Java");
        category.setSlug("java");
        category.setType("NOTE");
        return category;
    }
}
