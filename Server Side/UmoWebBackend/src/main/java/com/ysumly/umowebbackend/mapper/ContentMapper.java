package com.ysumly.umowebbackend.mapper;

import com.ysumly.umowebbackend.model.dto.ContentQuery;
import com.ysumly.umowebbackend.model.entity.Content;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ContentMapper {
    // 公开端
    List<Content> findPublished(@Param("query") ContentQuery query,
                                @Param("categoryIds") List<Long> categoryIds);
    long countPublished(@Param("query") ContentQuery query,
                        @Param("categoryIds") List<Long> categoryIds);
    Content findBySlug(String slug);
    Content findPreviousPublished(@Param("publishedAt") LocalDateTime publishedAt,
                                  @Param("id") Long id);
    Content findNextPublished(@Param("publishedAt") LocalDateTime publishedAt,
                              @Param("id") Long id);

    // 管理端
    List<Content> findAll(@Param("query") ContentQuery query,
                          @Param("categoryIds") List<Long> categoryIds);
    long countAll(@Param("query") ContentQuery query,
                  @Param("categoryIds") List<Long> categoryIds);
    Content findById(Long id);
    long countBySlug(@Param("slug") String slug, @Param("excludeId") Long excludeId);
    List<String> findAllBodyPaths();
    List<Content> findAllPublishedForIndex();
    void insert(Content content);
    void update(Content content);
    void delete(Long id);

    // 搜索
    List<Content> search(@Param("q") String q,
                         @Param("offset") int offset,
                         @Param("size") int size);
    long countSearch(@Param("q") String q);
}
