package com.ysumly.umowebbackend.mapper;

import com.ysumly.umowebbackend.model.dto.ContentQuery;
import com.ysumly.umowebbackend.model.entity.Content;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ContentMapper {
    // 公开端
    List<Content> findPublished(ContentQuery query);
    long countPublished(ContentQuery query);
    Content findBySlug(String slug);

    // 管理端
    List<Content> findAll(ContentQuery query);
    long countAll(ContentQuery query);
    Content findById(Long id);
    long countBySlug(@Param("slug") String slug, @Param("excludeId") Long excludeId);
    void insert(Content content);
    void update(Content content);
    void delete(Long id);

    // 搜索
    List<Content> search(@Param("q") String q,
                         @Param("offset") int offset,
                         @Param("size") int size);
    long countSearch(@Param("q") String q);
}
