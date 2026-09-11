package com.ysumly.umowebbackend.mapper;

import com.ysumly.umowebbackend.model.entity.Image;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ImageMapper {
    void insert(Image image);
    Image findById(Long id);
}
