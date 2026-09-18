package com.ysumly.umowebbackend.mapper;

import com.ysumly.umowebbackend.model.entity.AiTransformModeVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AiTransformModeVersionMapper {
    AiTransformModeVersion findVersion(@Param("modeId") Long modeId,
                                       @Param("versionNo") int versionNo);
    AiTransformModeVersion findCurrentVersion(Long modeId);
    List<AiTransformModeVersion> findVersions(Long modeId);
    int insertVersion(AiTransformModeVersion version);
    int deleteVersionsBefore(@Param("modeId") Long modeId,
                             @Param("minimumVersionNo") int minimumVersionNo);
}
