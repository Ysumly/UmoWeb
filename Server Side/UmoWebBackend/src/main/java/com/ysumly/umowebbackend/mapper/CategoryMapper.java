package com.ysumly.umowebbackend.mapper;

import com.ysumly.umowebbackend.model.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface CategoryMapper {
    List<Category> findAll();
    List<Category> findByType(@Param("type") String type);
    Category findById(Long id);
    void insert(Category category);
    void update(Category category);
    void delete(Long id);
    List<Category> findByIds(@Param("ids") List<Long> ids);
    long countChildren(Long parentId);
}
