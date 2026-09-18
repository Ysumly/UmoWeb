package com.ysumly.umowebbackend.mapper;

import com.ysumly.umowebbackend.model.entity.AiTransformMode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AiTransformModeMapper {
    List<AiTransformMode> findAll();
    AiTransformMode findById(Long id);
    AiTransformMode findByKey(String modeKey);
    AiTransformMode findEnabledByKey(String modeKey);
    int insert(AiTransformMode mode);
    int updateMetadata(AiTransformMode mode);
    int updateCurrentVersion(@Param("id") Long id,
                             @Param("expectedVersion") int expectedVersion,
                             @Param("newVersion") int newVersion);
}
