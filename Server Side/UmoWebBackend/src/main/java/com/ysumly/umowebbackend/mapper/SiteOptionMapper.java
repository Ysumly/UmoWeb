package com.ysumly.umowebbackend.mapper;

import com.ysumly.umowebbackend.model.entity.SiteOption;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface SiteOptionMapper {
    List<SiteOption> findAll();
    SiteOption findByKey(String optionKey);
    void upsert(@Param("key") String key, @Param("value") String value);
}
