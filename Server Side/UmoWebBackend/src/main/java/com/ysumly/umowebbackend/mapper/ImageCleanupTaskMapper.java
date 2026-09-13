package com.ysumly.umowebbackend.mapper;

import com.ysumly.umowebbackend.model.entity.ImageCleanupTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ImageCleanupTaskMapper {
    void insert(ImageCleanupTask task);
    List<ImageCleanupTask> findAll();
    void delete(Long id);
    void recordFailure(@Param("id") Long id, @Param("error") String error);
}
