package com.ysumly.umowebbackend.mapper;

import com.ysumly.umowebbackend.model.dto.ContentTagLink;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ContentTagMapper {
    void insert(@Param("contentId") Long contentId,
                @Param("tagId") Long tagId);
    void deleteByContentId(Long contentId);
    List<Long> findTagIdsByContentId(Long contentId);
    List<ContentTagLink> findLinksByContentIds(@Param("contentIds") List<Long> contentIds);
    long countContentsByTagId(Long tagId);
}
