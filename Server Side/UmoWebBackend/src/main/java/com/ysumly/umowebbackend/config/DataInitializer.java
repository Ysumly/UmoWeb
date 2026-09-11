package com.ysumly.umowebbackend.config;

import com.ysumly.umowebbackend.mapper.UserMapper;
import com.ysumly.umowebbackend.model.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Value("${app.init.admin-username:admin}")
    private String adminUsername;

    @Value("${app.init.admin-password:admin123}")
    private String adminPassword;

    @Bean
    public ApplicationRunner initAdminUser(UserMapper userMapper,
                                           PasswordEncoder passwordEncoder) {
        return args -> {
            if (userMapper.count() == 0) {
                User user = new User();
                user.setUsername(adminUsername);
                user.setPasswordHash(passwordEncoder.encode(adminPassword));
                userMapper.insert(user);
                log.info("默认管理员已创建: username={} (请尽快修改密码)", adminUsername);
            } else {
                log.info("管理员用户已存在，跳过初始化");
            }
        };
    }
}
