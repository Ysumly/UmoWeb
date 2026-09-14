package com.ysumly.umowebbackend.mapper;

import com.ysumly.umowebbackend.model.entity.Image;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ImageMapper {
    void insert(Image image);
    Image findById(Long id);
    List<Image> findAll();
    void delete(Long id);
}
