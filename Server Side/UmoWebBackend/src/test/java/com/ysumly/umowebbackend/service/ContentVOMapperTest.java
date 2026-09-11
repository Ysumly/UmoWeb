package com.ysumly.umowebbackend.service;

import com.ysumly.umowebbackend.mapper.CategoryMapper;
import com.ysumly.umowebbackend.mapper.ContentCategoryMapper;
import com.ysumly.umowebbackend.mapper.ContentTagMapper;
import com.ysumly.umowebbackend.mapper.TagMapper;
import com.ysumly.umowebbackend.model.dto.ContentCategoryLink;
import com.ysumly.umowebbackend.model.dto.ContentTagLink;
import com.ysumly.umowebbackend.model.entity.Category;
import com.ysumly.umowebbackend.model.entity.Content;
import com.ysumly.umowebbackend.model.entity.Tag;
import com.ysumly.umowebbackend.model.vo.ContentListVO;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class ContentVOMapperTest {

    private final ContentCategoryMapper contentCategoryMapper = mock(ContentCategoryMapper.class);
    private final ContentTagMapper contentTagMapper = mock(ContentTagMapper.class);
    private final CategoryMapper categoryMapper = mock(CategoryMapper.class);
    private final TagMapper tagMapper = mock(TagMapper.class);
    private final ContentVOMapper mapper = new ContentVOMapper(
            contentCategoryMapper,
            contentTagMapper,
            categoryMapper,
            tagMapper,
            new ObjectMapper());

    @Test
    void canBeCreatedWithJacksonAutoConfiguration() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ContentCategoryMapper.class, () -> contentCategoryMapper);
            context.registerBean(ContentTagMapper.class, () -> contentTagMapper);
            context.registerBean(CategoryMapper.class, () -> categoryMapper);
            context.registerBean(TagMapper.class, () -> tagMapper);
            context.register(JacksonAutoConfiguration.class, ContentVOMapper.class);

            context.refresh();

            assertThat(context.getBean(ContentVOMapper.class)).isNotNull();
        }
    }

    @Test
    void assemblesThreeContentsWithConstantAssociationQueries() {
        List<Content> contents = List.of(
                content(1L, "one"),
                content(2L, "two"),
                content(3L, "three"));

        ContentCategoryLink firstCategory = categoryLink(1L, 10L);
        ContentCategoryLink secondCategory = categoryLink(2L, 11L);
        ContentTagLink firstTag = tagLink(1L, 20L);
        ContentTagLink thirdTag = tagLink(3L, 21L);
        when(contentCategoryMapper.findLinksByContentIds(anyList()))
                .thenReturn(List.of(firstCategory, secondCategory));
        when(contentTagMapper.findLinksByContentIds(anyList()))
                .thenReturn(List.of(firstTag, thirdTag));
        when(categoryMapper.findByIds(anyList())).thenReturn(List.of(
                category(10L, "java"),
                category(11L, "spring")));
        when(tagMapper.findByIds(anyList())).thenReturn(List.of(
                tag(20L, "backend"),
                tag(21L, "database")));

        List<ContentListVO> result = mapper.toListVOs(contents);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getCategories()).extracting("slug").containsExactly("java");
        assertThat(result.get(0).getTags()).extracting("slug").containsExactly("backend");
        assertThat(result.get(1).getCategories()).extracting("slug").containsExactly("spring");
        assertThat(result.get(1).getTags()).isEmpty();
        assertThat(result.get(2).getCategories()).isEmpty();
        assertThat(result.get(2).getTags()).extracting("slug").containsExactly("database");

        verify(contentCategoryMapper, times(1)).findLinksByContentIds(anyList());
        verify(contentTagMapper, times(1)).findLinksByContentIds(anyList());
        verify(categoryMapper, times(1)).findByIds(anyList());
        verify(tagMapper, times(1)).findByIds(anyList());
        verify(contentCategoryMapper, never()).findCategoryIdsByContentId(any());
        verify(contentTagMapper, never()).findTagIdsByContentId(any());
    }

    @Test
    void mapsStatusForListAndDetailResponses() {
        Content content = content(1L, "draft-note");
        content.setStatus("DRAFT");
        when(contentCategoryMapper.findLinksByContentIds(anyList())).thenReturn(List.of());
        when(contentTagMapper.findLinksByContentIds(anyList())).thenReturn(List.of());

        ContentListVO listItem = mapper.toListVO(content);

        assertThat(listItem.getStatus()).isEqualTo("DRAFT");
        assertThat(mapper.toDetailVO(content, "body").getStatus()).isEqualTo("DRAFT");
    }

    private Content content(Long id, String slug) {
        Content content = new Content();
        content.setId(id);
        content.setTitle(slug);
        content.setSlug(slug);
        content.setType("NOTE");
        return content;
    }

    private ContentCategoryLink categoryLink(Long contentId, Long categoryId) {
        ContentCategoryLink link = new ContentCategoryLink();
        link.setContentId(contentId);
        link.setCategoryId(categoryId);
        return link;
    }

    private ContentTagLink tagLink(Long contentId, Long tagId) {
        ContentTagLink link = new ContentTagLink();
        link.setContentId(contentId);
        link.setTagId(tagId);
        return link;
    }

    private Category category(Long id, String slug) {
        Category category = new Category();
        category.setId(id);
        category.setName(slug);
        category.setSlug(slug);
        category.setType("NOTE");
        return category;
    }

    private Tag tag(Long id, String slug) {
        Tag tag = new Tag();
        tag.setId(id);
        tag.setName(slug);
        tag.setSlug(slug);
        return tag;
    }
}
