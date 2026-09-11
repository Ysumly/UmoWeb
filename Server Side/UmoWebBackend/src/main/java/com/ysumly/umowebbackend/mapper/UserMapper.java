package com.ysumly.umowebbackend.mapper;

import com.ysumly.umowebbackend.model.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {
    User findByUsername(String username);
    void insert(User user);
    void updatePassword(@Param("username") String username,
                        @Param("passwordHash") String passwordHash);
    long count();
}
