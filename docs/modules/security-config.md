# 安全与配置实现

> 基线日期: 2026-09-18
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
| `AiProperties` | AI 开关、限制和全局 DeepSeek 配置 |
| `AiHttpClientConfig` | 带连接/读取超时的 DeepSeek `RestClient` |
| `AiRuntimeConfigValidator` | AI 启用时的密钥、模型和正数限制启动校验 |
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
- 默认使用 `remoteAddr`。仅当直连地址匹配 `app.security.trusted-proxies` 中的精确 IP 或 CIDR 时读取 `X-Forwarded-For`。
- 配置项支持逗号分隔的 IPv4/IPv6 地址和 CIDR，例如 `10.0.0.2,10.0.0.0/8`；非法条目会阻止应用启动。
- `ClientIpResolver` 同时提供生产构造器和测试用构造器，生产构造器显式标注 `@Autowired`，保证 Spring 容器可以实例化。

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
        .allowedOrigins(allowedOrigins)
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .allowCredentials(true);
```

允许来源从 `app.cors.allowed-origins` 读取，支持逗号分隔。开发默认是 Vite 地址，
生产通过 `CORS_ALLOWED_ORIGINS` 设置实际域名；空列表会启动失败。

### 4.3 图片静态资源

```java
registry.addResourceHandler("/images/**")
        .addResourceLocations(imageResourceLocation());
```

`imageResourceLocation()` 将 `app.storage-path/images` 规范化为绝对 `file:` URI，避免把占位符当作文本路径。
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
app:
  storage-path: ./data
  ai:
    enabled: ${APP_AI_ENABLED:false}
    max-input-chars: 20000
    max-output-chars: 60000
    timeout-seconds: 180
    max-requests-per-window: 5
    rate-limit-window-seconds: 600
    max-concurrent-requests: 1
    deepseek:
      base-url: ${DEEPSEEK_BASE_URL:https://api.deepseek.com}
      api-key: ${DEEPSEEK_API_KEY:}
      model: ${DEEPSEEK_MODEL:}
  cors:
    allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:5173}
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

### 6.1 AI 运行时边界

- `APP_AI_ENABLED=false` 时不需要密钥或模型，应用正常启动；启用时
  `DEEPSEEK_API_KEY` 和 `DEEPSEEK_MODEL` 必须非空，否则 `AiRuntimeConfigValidator`
  在启动阶段失败。
- 供应商请求使用带连接和读取超时的 Spring `RestClient`，不再依赖 Spring AI；模型配置为全局配置。
- `AiRequestGuard` 使用进程内信号量和时间窗口：10 分钟最多 5 次且同时最多 1 个请求。
  多后端实例部署时需要共享限流存储或网关限流。
- Provider 不自动重试。认证、余额和 500/503 统一返回 503，上游 429 返回 429，
  非法请求/响应返回 502，超时返回 504。
- 运行日志只允许请求 ID、模式、版本、字符数、Token、耗时、状态和错误分类；
  正文、转换结果、系统提示词和 API Key 不进入日志。

Docker Compose 将 Nginx 固定为 `172.30.0.10`，后端只信任 `172.30.0.10/32`。
Nginx 使用 `$remote_addr` 覆盖客户端请求中的 `X-Forwarded-For`，避免外来转发头绕过限流。

Nginx 另通过 `ACCESS_TRUSTED_PROXIES` 配置自己的上游可信代理。默认列表为空，不采用任何
客户端提供的 `X-Forwarded-For`；只有直连来源匹配可信 IP/CIDR 时才启用 real_ip 解析。
该项与后端 `TRUSTED_PROXIES` 是两层独立信任边界，最终代理拓扑变化时必须同时复核。

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

## 9. 访问安全日志

Nginx 的 `umoweb_security` JSON 格式严格记录六个字段：

```text
time, ip, method, path, status, bytes
```

- `path` 来自 `$request_uri` 的问号前部分，保留 SPA 真实路由但不记录查询字符串。
- `$time_iso8601` 提供带时区的 ISO 8601 时间，`$body_bytes_sent` 为响应字节数。
- 不记录 `$request`、请求体、Cookie、Authorization、Referer 或 User-Agent。
- 原始日志写入 `access/logs` 绑定目录，目录权限目标为 `0750`、文件为 `0640`。
- `scripts/access/` 每日轮转和 gzip；原始日志默认 30 天，仅允许配置 7–30 天。
- 长期聚合只保存请求数、每日独立 IP 数量、状态/方法分布、字节数、路径排行和解析错误数，
  不保存任何原始 IP 或跨日标识。
- 含原始 IP 的短期 HTML 报表由专用非 root 服务提供，只监听 `127.0.0.1`，不提供目录列表、
  不缓存，也不启用持久化原始 IP 数据库。

公开 `/privacy` 与运行时 `/privacy-config.json` 使用同一份保留期配置。配置不可读时页面明确
显示策略不可用，而不是回退展示可能错误的天数。

---

## 10. 安全缺口

| 风险 | 当前事实 |
|---|---|
| 秘密切口 | 后端 YAML 不再承载前端管理路径，前端使用 `VITE_ADMIN_PATH` |
| 爬虫控制 | 有 `noindex`，无 `robots.txt` |
| 路径穿越 | slug/bookSlug 和安全化路径已检查，读写删必须位于 storage root |
| 上传校验 | MIME、文件签名和固定扩展名同时校验 |
| 搜索限流 | 仅信任显式代理，使用原子更新并定期清理 |
| 反向代理 | Nginx 覆盖 `X-Forwarded-For`，后端仅信任 Compose 专用 CIDR |
| 生产 CORS | 通过 `CORS_ALLOWED_ORIGINS` 配置 |
| 数据库完整性 | 新库已含外键/索引；旧库需执行兼容迁移 |
| 文件事务 | 使用临时文件、提交后清理和回滚恢复策略 |

这些问题已记录在 [audit-log.md](../project/audit-log.md)。

---

## 11. 测试现状

边界测试覆盖了部分 401、400、409 和 429 的 Service 异常路径，但没有启动真实 Web 容器和拦截器链。

已新增：

- JWT 过期、tokenVersion 和旧 token 失效测试。
- 登录失败限流、搜索限流和可信代理测试。
- 可信代理精确 IP、IPv4/IPv6 CIDR、非法配置和多级转发链测试。
- 路径穿越、临时文件、回滚和原子替换测试。
- 伪造 MIME、空原始文件名和上传数据库失败清理测试。
- 六字段日志解析、敏感字段拒绝、聚合去重、保留边界、可信代理生成和报表转义测试。
- Nginx 容器级合成请求验证，覆盖查询参数、Cookie、Authorization、请求体和伪造转发头。

仍缺少：

- 生产代理环境测试。
- 多实例共享限流测试。
