package com.ysumly.umowebbackend.service.impl.open;

import com.ysumly.umowebbackend.common.util.FileUtil;
import com.ysumly.umowebbackend.mapper.CategoryMapper;
import com.ysumly.umowebbackend.mapper.ContentCategoryMapper;
import com.ysumly.umowebbackend.mapper.ContentMapper;
import com.ysumly.umowebbackend.mapper.ContentTagMapper;
import com.ysumly.umowebbackend.mapper.TagMapper;
import com.ysumly.umowebbackend.model.dto.ContentCategoryLink;
import com.ysumly.umowebbackend.model.dto.ContentTagLink;
import com.ysumly.umowebbackend.model.entity.Category;
import com.ysumly.umowebbackend.model.entity.Content;
import com.ysumly.umowebbackend.model.entity.Tag;
import com.ysumly.umowebbackend.service.ContentVOMapper;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ContentServiceImplTest {

    private final ContentMapper contentMapper = mock(ContentMapper.class);
    private final ContentCategoryMapper contentCategoryMapper = mock(ContentCategoryMapper.class);
    private final ContentTagMapper contentTagMapper = mock(ContentTagMapper.class);
    private final CategoryMapper categoryMapper = mock(CategoryMapper.class);
    private final TagMapper tagMapper = mock(TagMapper.class);
    private final FileUtil fileUtil = mock(FileUtil.class);

    @Test
    void publicDetailIncludesCategoriesAndTags() throws Exception {
        Content content = new Content();
        content.setId(1L);
        content.setTitle("Article");
        content.setSlug("article");
        content.setBodyPath("contents/NOTE/article.md");
        content.setType("NOTE");
        content.setStatus("PUBLISHED");
        content.setPublishedAt(LocalDateTime.of(2026, 6, 20, 10, 0));
        when(contentMapper.findBySlug("article")).thenReturn(content);
        when(fileUtil.readMarkdown("contents/NOTE/article.md")).thenReturn("# Article");

        ContentCategoryLink categoryLink = new ContentCategoryLink();
        categoryLink.setContentId(1L);
        categoryLink.setCategoryId(10L);
        when(contentCategoryMapper.findLinksByContentIds(List.of(1L)))
                .thenReturn(List.of(categoryLink));
        Category category = new Category();
        category.setId(10L);
        category.setName("Java");
        category.setSlug("java");
        category.setType("NOTE");
        when(categoryMapper.findByIds(anyList())).thenReturn(List.of(category));

        ContentTagLink tagLink = new ContentTagLink();
        tagLink.setContentId(1L);
        tagLink.setTagId(20L);
        when(contentTagMapper.findLinksByContentIds(List.of(1L)))
                .thenReturn(List.of(tagLink));
        Tag tag = new Tag();
        tag.setId(20L);
        tag.setName("Backend");
        tag.setSlug("backend");
        when(tagMapper.findByIds(anyList())).thenReturn(List.of(tag));

        ContentVOMapper voMapper = new ContentVOMapper(
                contentCategoryMapper,
                contentTagMapper,
                categoryMapper,
                tagMapper,
                new ObjectMapper());
        ContentServiceImpl service = new ContentServiceImpl(contentMapper, fileUtil, voMapper);

        var detail = service.getBySlug("article");

        assertThat(detail.getBody()).isEqualTo("# Article");
        assertThat(detail.getCategories()).extracting("slug").containsExactly("java");
        assertThat(detail.getTags()).extracting("slug").containsExactly("backend");
    }

    @Test
    void publicDetailIncludesOlderPreviousAndNewerNext() throws Exception {
        Content current = content(
                2L,
                "current",
                LocalDateTime.of(2026, 6, 22, 10, 0));
        Content older = content(
                1L,
                "older",
                LocalDateTime.of(2026, 6, 20, 10, 0));
        Content newer = content(
                3L,
                "newer",
                LocalDateTime.of(2026, 6, 24, 10, 0));

        when(contentMapper.findBySlug("current")).thenReturn(current);
        when(contentMapper.findPreviousPublished(current.getPublishedAt(), current.getId()))
                .thenReturn(older);
        when(contentMapper.findNextPublished(current.getPublishedAt(), current.getId()))
                .thenReturn(newer);
        when(fileUtil.readMarkdown(current.getBodyPath())).thenReturn("# Current");

        ContentServiceImpl service = service();

        var detail = service.getBySlug("current");

        assertThat(detail.getPrevious()).extracting("id", "title", "slug", "publishedAt")
                .containsExactly(1L, "older", "older", older.getPublishedAt());
        assertThat(detail.getNext()).extracting("id", "title", "slug", "publishedAt")
                .containsExactly(3L, "newer", "newer", newer.getPublishedAt());
    }

    @Test
    void publicDetailReturnsNullNeighborsAtPublishedBoundaries() throws Exception {
        Content only = content(
                1L,
                "only",
                LocalDateTime.of(2026, 6, 20, 10, 0));

        when(contentMapper.findBySlug("only")).thenReturn(only);
        when(contentMapper.findPreviousPublished(only.getPublishedAt(), only.getId()))
                .thenReturn(null);
        when(contentMapper.findNextPublished(only.getPublishedAt(), only.getId()))
                .thenReturn(null);

        ContentServiceImpl service = service();

        var detail = service.getBySlug("only");

        assertThat(detail.getPrevious()).isNull();
        assertThat(detail.getNext()).isNull();
    }

    private ContentServiceImpl service() {
        ContentVOMapper voMapper = new ContentVOMapper(
                contentCategoryMapper,
                contentTagMapper,
                categoryMapper,
                tagMapper,
                new ObjectMapper());
        return new ContentServiceImpl(contentMapper, fileUtil, voMapper);
    }

    private Content content(Long id, String slug, LocalDateTime publishedAt) {
        Content content = new Content();
        content.setId(id);
        content.setTitle(slug);
        content.setSlug(slug);
        content.setBodyPath("contents/NOTE/" + slug + ".md");
        content.setType("NOTE");
        content.setStatus("PUBLISHED");
        content.setPublishedAt(publishedAt);
        return content;
    }
}
