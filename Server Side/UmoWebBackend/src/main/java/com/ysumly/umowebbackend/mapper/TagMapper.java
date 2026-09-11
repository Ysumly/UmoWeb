package com.ysumly.umowebbackend.mapper;

import com.ysumly.umowebbackend.model.entity.Tag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface TagMapper {
    List<Tag> findAll();
    Tag findById(Long id);
    void insert(Tag tag);
    void update(Tag tag);
    void delete(Long id);
    List<Tag> findByIds(@Param("ids") List<Long> ids);
    List<Tag> findByContentId(Long contentId);
}
