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
import com.ysumly.umowebbackend.model.vo.CategoryTreeVO;
import com.ysumly.umowebbackend.model.vo.ContentDetailVO;
import com.ysumly.umowebbackend.model.vo.ContentListVO;
import com.ysumly.umowebbackend.model.vo.TagVO;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ContentVOMapper {

    private final ContentCategoryMapper contentCategoryMapper;
    private final ContentTagMapper contentTagMapper;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final ObjectMapper objectMapper;

    public ContentVOMapper(ContentCategoryMapper contentCategoryMapper,
                           ContentTagMapper contentTagMapper,
                           CategoryMapper categoryMapper,
                           TagMapper tagMapper,
                           ObjectMapper objectMapper) {
        this.contentCategoryMapper = contentCategoryMapper;
        this.contentTagMapper = contentTagMapper;
        this.categoryMapper = categoryMapper;
        this.tagMapper = tagMapper;
        this.objectMapper = objectMapper;
    }

    public ContentListVO toListVO(Content content) {
        return toListVOs(List.of(content)).get(0);
    }

    public ContentDetailVO toDetailVO(Content content, String body) {
        ContentDetailVO detail = new ContentDetailVO();
        fillListFields(detail, toListVO(content));
        detail.setBody(body);
        return detail;
    }

    public List<ContentListVO> toListVOs(List<Content> contents) {
        if (contents.isEmpty()) {
            return List.of();
        }

        List<Long> contentIds = contents.stream().map(Content::getId).toList();
        Map<Long, List<Long>> categoryIdsByContent = groupCategoryIds(
                contentCategoryMapper.findLinksByContentIds(contentIds));
        Map<Long, List<Long>> tagIdsByContent = groupTagIds(
                contentTagMapper.findLinksByContentIds(contentIds));

        Set<Long> categoryIds = categoryIdsByContent.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toSet());
        Set<Long> tagIds = tagIdsByContent.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toSet());

        Map<Long, Category> categories = loadCategories(categoryIds);
        Map<Long, Tag> tags = loadTags(tagIds);

        List<ContentListVO> result = new ArrayList<>(contents.size());
        for (Content content : contents) {
            ContentListVO vo = new ContentListVO();
            fillBasicFields(vo, content);
            vo.setCategories(categoryIdsByContent
                    .getOrDefault(content.getId(), List.of())
                    .stream()
                    .map(categories::get)
                    .filter(Objects::nonNull)
                    .map(this::toCategoryVO)
                    .toList());
            vo.setTags(tagIdsByContent
                    .getOrDefault(content.getId(), List.of())
                    .stream()
                    .map(tags::get)
                    .filter(Objects::nonNull)
                    .map(this::toTagVO)
                    .toList());
            result.add(vo);
        }
        return result;
    }

    private void fillListFields(ContentListVO target, ContentListVO source) {
        target.setId(source.getId());
        target.setTitle(source.getTitle());
        target.setSlug(source.getSlug());
        target.setSummary(source.getSummary());
        target.setType(source.getType());
        target.setStatus(source.getStatus());
        target.setCategories(source.getCategories());
        target.setTags(source.getTags());
        target.setMetadata(source.getMetadata());
        target.setPublishedAt(source.getPublishedAt());
        target.setScheduledAt(source.getScheduledAt());
    }

    private void fillBasicFields(ContentListVO vo, Content content) {
        vo.setId(content.getId());
        vo.setTitle(content.getTitle());
        vo.setSlug(content.getSlug());
        vo.setSummary(content.getSummary());
        vo.setType(content.getType());
        vo.setStatus(content.getStatus());
        vo.setPublishedAt(content.getPublishedAt());
        vo.setScheduledAt(content.getScheduledAt());
        vo.setMetadata(parseMetadata(content.getMetadata()));
    }

    private Map<String, Object> parseMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(metadata, Map.class);
            return parsed;
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private Map<Long, List<Long>> groupCategoryIds(List<ContentCategoryLink> links) {
        return links.stream().collect(Collectors.groupingBy(
                ContentCategoryLink::getContentId,
                LinkedHashMap::new,
                Collectors.mapping(ContentCategoryLink::getCategoryId, Collectors.toList())));
    }

    private Map<Long, List<Long>> groupTagIds(List<ContentTagLink> links) {
        return links.stream().collect(Collectors.groupingBy(
                ContentTagLink::getContentId,
                LinkedHashMap::new,
                Collectors.mapping(ContentTagLink::getTagId, Collectors.toList())));
    }

    private Map<Long, Category> loadCategories(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return categoryMapper.findByIds(new ArrayList<>(ids)).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
    }

    private Map<Long, Tag> loadTags(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return tagMapper.findByIds(new ArrayList<>(ids)).stream()
                .collect(Collectors.toMap(Tag::getId, Function.identity()));
    }

    private CategoryTreeVO toCategoryVO(Category category) {
        CategoryTreeVO vo = new CategoryTreeVO();
        vo.setId(category.getId());
        vo.setName(category.getName());
        vo.setSlug(category.getSlug());
        vo.setType(category.getType());
        return vo;
    }

    private TagVO toTagVO(Tag tag) {
        TagVO vo = new TagVO();
        vo.setId(tag.getId());
        vo.setName(tag.getName());
        vo.setSlug(tag.getSlug());
        return vo;
    }
}
