package com.ysumly.umowebbackend.mapper;

import com.ysumly.umowebbackend.model.dto.ContentCategoryLink;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ContentCategoryMapper {
    void insert(@Param("contentId") Long contentId,
                @Param("categoryId") Long categoryId);
    void insertIgnore(@Param("contentId") Long contentId,
                      @Param("categoryId") Long categoryId);
    void deleteLinks(@Param("contentIds") List<Long> contentIds,
                     @Param("categoryIds") List<Long> categoryIds);
    void deleteByContentId(Long contentId);
    List<Long> findCategoryIdsByContentId(Long contentId);
    List<ContentCategoryLink> findLinksByContentIds(@Param("contentIds") List<Long> contentIds);
    long countContentsByCategoryId(Long categoryId);
}
