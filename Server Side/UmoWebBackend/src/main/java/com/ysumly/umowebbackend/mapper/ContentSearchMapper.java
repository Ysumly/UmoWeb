package com.ysumly.umowebbackend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ContentSearchMapper {
    void upsert(@Param("contentId") Long contentId, @Param("bodyText") String bodyText);

    void deleteByContentId(Long contentId);

    void deleteNotPublished();

    long count();
}
