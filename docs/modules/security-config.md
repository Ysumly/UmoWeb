# 安全与配置实现

> 基线日期: 2026-09-11
> 路径: `config/`、`common/util/JwtUtil.java`

---

## 1. Config 文件

| 文件 | 作用 |
|---|---|
| `WebConfig` | 注册拦截器、CORS、图片静态映射 |
| `SecurityConfig` | BCrypt PasswordEncoder Bean |
| `AdminInterceptor` | 管理端 JWT 校验 |
| `RateLimitInterceptor` | 搜索接口内存限流 |
| `LoginAttemptService` | 登录失败次数保护 |
| `ClientIpResolver` | 可信代理和客户端 IP 解析 |
| `SecurityConfigValidator` | 生产默认凭据启动校验 |
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
3. 校验 JWT 签名和过期时间。
4. 解析 username 和 `ver` tokenVersion。
5. 查询当前用户并比较 tokenVersion；不匹配时返回 401。
6. 写入 request attribute `adminUsername`。

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
- 使用 `compute` 原子检查/更新时间，避免并发请求同时通过。
- 请求过频时抛出 `BusinessException(429, ...)`。
- 每隔 256 次操作清理过期记录。
- 默认使用 `remoteAddr`。仅当直连地址在 `app.security.trusted-proxies` 中时读取 `X-Forwarded-For`。

多实例部署时各实例仍为独立内存限流。

### 3.1 登录失败保护

`LoginAttemptService` 以 `username|clientIp` 为 key：

- 默认 15 分钟窗口内最多失败 5 次。
- 达上限后返回 429。
- 登录成功清除该 key。
- 过期记录定期清理。

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
  security:
    allow-default-credentials: false
    trusted-proxies: ${TRUSTED_PROXIES:}
    login-max-failures: 5
    login-window-seconds: 900
    search-rate-limit-seconds: 10
  jwt:
    secret: ${JWT_SECRET:change-me-in-production-this-is-a-default-only}
    expiration-hours: 24
  init:
    admin-username: ${INIT_ADMIN_USER:admin}
    admin-password: ${INIT_ADMIN_PASS:admin123}
```

`spring.profiles.default=dev`，`application-dev.yml` 明确允许默认凭据以便本地启动。
`SPRING_PROFILES_ACTIVE=prod` 时 `application-prod.yml` 禁止默认凭据，
`SecurityConfigValidator` 会在 JWT secret 或管理员密码仍为默认值时拒绝启动。

管理端前端路径不再由后端 YAML 控制，前端通过 `VITE_ADMIN_PATH` 配置并默认 `/secret-admin`，
示例见 `Client Side/umo-web-frontend/.env.example`。

---

## 7. 管理员初始化

`DataInitializer` 是 `ApplicationRunner`：

- 查询 `users` 总数。
- 数量为 0 时创建管理员。
- 默认用户名 `admin`，默认密码 `admin123`。
- 密码使用 BCrypt。
- 已存在任意用户时跳过。

生产环境必须设置 `SPRING_PROFILES_ACTIVE=prod`，并覆盖默认密码和 JWT secret。
校验失败只报告配置项名称，不输出密码或 secret。

---

## 8. JWT

实现位于 `JwtUtil`：

- secret 经 SHA-256 后作为 HMAC key。
- token subject 为 username，包含 `ver` tokenVersion。
- 默认 24 小时过期。
- `validate` 捕获签名/过期等解析异常并返回 false。

修改密码会让数据库 `token_version` 原子递增，使旧 token 立即失效。
当前没有 refresh token、黑名单或完整的多设备会话管理。

---

## 9. 安全缺口

| 风险 | 当前事实 |
|---|---|
| 秘密切口 | 后端 YAML 不再承载前端管理路径，前端使用 `VITE_ADMIN_PATH` |
| 爬虫控制 | 有 `noindex`，无 `robots.txt` |
| 路径穿越 | slug/bookSlug 和安全化路径已检查，读写删必须位于 storage root |
| 上传校验 | MIME、文件签名和固定扩展名同时校验 |
| 搜索限流 | 仅信任显式代理，使用原子更新并定期清理 |
| 生产 CORS | 写死 localhost |
| 数据库完整性 | 新库已含外键/索引；旧库需执行兼容迁移 |
| 文件事务 | 使用临时文件、提交后清理和回滚恢复策略 |

这些问题已记录在 [audit-log.md](../project/audit-log.md)。

---

## 10. 测试现状

边界测试覆盖了部分 401、400、409 和 429 的 Service 异常路径，但没有启动真实 Web 容器和拦截器链。

已新增：

- JWT 过期、tokenVersion 和旧 token 失效测试。
- 登录失败限流、搜索限流和可信代理测试。
- 路径穿越、临时文件、回滚和原子替换测试。
- 伪造 MIME、空原始文件名和上传数据库失败清理测试。

仍缺少：

- CORS 和生产代理环境测试。
- 多实例共享限流测试。
