package com.ysumly.umowebbackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AdminInterceptor adminInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;
    private final String[] allowedOrigins;

    public WebConfig(AdminInterceptor adminInterceptor,
                     RateLimitInterceptor rateLimitInterceptor,
                     @Value("${app.cors.allowed-origins:http://localhost:5173}")
                     String allowedOrigins) {
        this.adminInterceptor = adminInterceptor;
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toArray(String[]::new);
        if (this.allowedOrigins.length == 0) {
            throw new IllegalStateException("app.cors.allowed-origins must not be empty");
        }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 管理端 JWT 认证
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns("/api/admin/login");

        // 搜索频率限制
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/public/contents/search");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/images/**")
                .addResourceLocations("file:${app.storage-path}/images/");
    }
}
