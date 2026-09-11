package com.ysumly.umowebbackend.service.impl.admin;

import com.ysumly.umowebbackend.common.exception.BusinessException;
import com.ysumly.umowebbackend.mapper.ContentTagMapper;
import com.ysumly.umowebbackend.mapper.TagMapper;
import com.ysumly.umowebbackend.model.entity.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TagManageServiceImplTest {

    private final TagMapper tagMapper = mock(TagMapper.class);
    private final ContentTagMapper contentTagMapper = mock(ContentTagMapper.class);
    private final TagManageServiceImpl service =
            new TagManageServiceImpl(tagMapper, contentTagMapper);

    @Test
    void deleteRejectsTagUsedByContent() {
        Tag tag = new Tag();
        tag.setId(1L);
        when(tagMapper.findById(1L)).thenReturn(tag);
        when(contentTagMapper.countContentsByTagId(1L)).thenReturn(2L);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(409);
        verify(tagMapper, never()).delete(anyLong());
    }

    @Test
    void deleteRemovesUnusedTag() {
        Tag tag = new Tag();
        tag.setId(1L);
        when(tagMapper.findById(1L)).thenReturn(tag);
        when(contentTagMapper.countContentsByTagId(1L)).thenReturn(0L);

        service.delete(1L);

        verify(tagMapper).delete(1L);
    }
}
