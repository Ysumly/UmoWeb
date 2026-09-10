# 安全与配置实现

> 基线日期: 2026-09-10
> 路径: `config/`、`common/util/JwtUtil.java`

---

## 1. Config 文件

| 文件 | 作用 |
|---|---|
| `WebConfig` | 注册拦截器、CORS、图片静态映射 |
| `SecurityConfig` | BCrypt PasswordEncoder Bean |
| `AdminInterceptor` | 管理端 JWT 校验 |
| `RateLimitInterceptor` | 搜索接口内存限流 |
| `DataInitializer` | 首次启动创建管理员 |

---

## 2. AdminInterceptor

拦截：

```text
/api/admin/**
```

排除：

```text
/api/admin/login
```

流程：

1. 读取 `Authorization`。
2. 检查前缀是否为 `Bearer `。
3. 校验 JWT 是否有效。
4. 解析 username。
5. 写入 request attribute `adminUsername`。

任何缺失或无效 token 都抛 `UnauthorizedException`，由全局异常处理返回 401。

---

## 3. RateLimitInterceptor

目标路径：

```text
/api/public/contents/search
```

实现：

- `ConcurrentHashMap<String, Long>` 保存 IP 到上次请求时间。
- 窗口固定 10 秒。
- 请求过频时抛出 `BusinessException(429, ...)`。
- IP 优先取 `X-Forwarded-For` 的第一个值，否则取 `remoteAddr`。

当前限制：

- 记录不会清理，长期运行会不断增长。
- 没有校验可信代理，客户端可自行伪造 `X-Forwarded-For`。
- 多实例部署时各实例独立限流。

---

## 4. WebConfig

### 4.1 拦截器

```java
registry.addInterceptor(adminInterceptor)
        .addPathPatterns("/api/admin/**")
        .excludePathPatterns("/api/admin/login");

registry.addInterceptor(rateLimitInterceptor)
        .addPathPatterns("/api/public/contents/search");
```

### 4.2 CORS

```java
registry.addMapping("/api/**")
        .allowedOrigins("http://localhost:5173")
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .allowCredentials(true);
```

开发环境允许 Vite 默认地址。生产域名和反向代理场景需要另行配置。

### 4.3 图片静态资源

```java
registry.addResourceHandler("/images/**")
        .addResourceLocations("file:${app.storage-path}/images/");
```

图片通过后端直接访问，前端开发环境由 Vite 代理 `/images`。

---

## 5. SecurityConfig

项目没有引入完整 Spring Security Filter Chain，只使用：

```xml
<artifactId>spring-security-crypto</artifactId>
```

提供：

```java
@Bean
PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

认证由自定义拦截器和 JwtUtil 完成。

---

## 6. application.yml

当前关键配置：

```yaml
server:
  port: 8080

spring:
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 50MB
  datasource:
    url: jdbc:mysql://localhost:3306/umo_blog?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
    username: ${DB_USER:root}
    password: ${DB_PASS:}
  ai:
    openai:
      api-key: ${OPENAI_API_KEY:sk-dummy-placeholder}

app:
  storage-path: ./data
  jwt:
    secret: ${JWT_SECRET:change-me-in-production-this-is-a-default-only}
    expiration-hours: 24
  admin-path: /secret-admin
  init:
    admin-username: ${INIT_ADMIN_USER:admin}
    admin-password: ${INIT_ADMIN_PASS:admin123}
```

`app.admin-path` 当前只存在于配置中，后端没有对应路由控制，前端也硬编码 `/secret-admin`。

---

## 7. 管理员初始化

`DataInitializer` 是 `ApplicationRunner`：

- 查询 `users` 总数。
- 数量为 0 时创建管理员。
- 默认用户名 `admin`，默认密码 `admin123`。
- 密码使用 BCrypt。
- 已存在任意用户时跳过。

生产环境必须覆盖默认密码和 JWT secret。

---

## 8. JWT

实现位于 `JwtUtil`：

- secret 经 SHA-256 后作为 HMAC key。
- token subject 为 username。
- 默认 24 小时过期。
- `validate` 捕获所有解析异常并返回 false。

当前没有 refresh token、token 撤销、黑名单或多设备管理。

---

## 9. 安全缺口

| 风险 | 当前事实 |
|---|---|
| 秘密切口 | `app.admin-path` 未使用 |
| 爬虫控制 | 有 `noindex`，无 `robots.txt` |
| 路径穿越 | slug/bookSlug 未检查最终路径是否在 storage root 内 |
| 上传校验 | 只校验 MIME，未校验文件签名 |
| 搜索限流 | 可伪造 XFF，Map 永不清空 |
| 生产 CORS | 写死 localhost |
| 数据库完整性 | 无外键约束 |
| 文件事务 | 文件操作不受数据库事务保护 |

这些问题已记录在 [audit-log.md](../project/audit-log.md)。

---

## 10. 测试现状

边界测试覆盖了部分 401、400、409 和 429 的 Service 异常路径，但没有启动真实 Web 容器和拦截器链。

缺少：

- JWT 过期和签名错误的真实测试。
- CORS 测试。
- 搜索限流测试。
- 路径穿越测试。
- 文件类型伪造测试。
