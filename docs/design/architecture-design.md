# UmoWeb 当前架构

> 基线日期: 2026-09-12
> 状态: 与当前代码一致
> 详细代码事实: [codebase-memory.md](../project/codebase-memory.md)

---

## 1. 系统组成

```text
Browser
  │
  ├── Vue 3 SPA (Vite 8)
  │     ├── /api/**  -> Vite dev proxy -> Spring Boot
  │     └── /images/** -> Vite dev proxy -> Spring Boot static resource
  │
  └── Spring Boot 4.1 / Java 17
        ├── Controller
        ├── Service
        ├── MyBatis Mapper
        ├── MySQL
        └── Local filesystem (app.storage-path)
```

项目是前后端分离的单体应用。后端提供 REST API；前端是纯 SPA，没有 SSR/SSG。

Docker 本地部署在上述拓扑外加一层 Nginx：

```text
Browser
  -> Nginx: ${APP_PORT:-8080} -> Vue 静态构建
                             -> /api/**、/images/** -> Spring Boot:8080
                                                        -> MySQL:3306
                                                        -> app_data 卷
```

MySQL 和 Spring Boot 不映射宿主端口；`mysql_data` 与 `app_data` 分别持久化数据库和
Markdown/图片。详细操作见 [docker-guide.md](../project/docker-guide.md)。

---

## 2. 后端分层

```text
com.ysumly.umowebbackend/
├── common/
│   ├── constant/       ContentType、ContentStatus
│   ├── exception/      BusinessException 体系和全局异常处理
│   └── util/           JwtUtil、FileUtil
├── config/             Web、JWT 拦截、限流、BCrypt、管理员初始化
├── controller/
│   ├── open/           公开端 4 个 Controller
│   └── admin/          管理端 6 个 Controller
├── mapper/             8 个 MyBatis Mapper
├── model/
│   ├── dto/            8 个请求/分页 DTO
│   ├── entity/         6 个数据库实体
│   └── vo/             6 个响应 VO
└── service/
    ├── open/           公开端接口与实际实现位于 impl/open
    ├── admin/          管理端接口与实际实现位于 impl/admin
    └── impl/           9 个 Service 实现
```

分层约定：

| 层 | 当前职责 |
|---|---|
| Controller | 路由、`@Valid`、调用 Service、设置 HTTP 状态 |
| Service | 业务组装、事务、文件读写、查询条件传递 |
| Mapper | SQL 执行和数据库映射 |
| Entity/DTO/VO | 数据库、请求、响应模型 |

---

## 3. 数据库

### 3.1 表关系

```text
users                          管理员账号，与内容无关联

contents ──< content_category >── categories
    │                                  │
    └────< content_tag >────────────── tags

images                         本地上传图片记录
site_options                   站点 KV 配置
```

### 3.2 表清单

| 表 | 用途 | 关键字段 |
|---|---|---|
| `users` | 管理员登录 | `username`、`password_hash` |
| `categories` | 分类层级 | `name`、`slug`、`parent_id`、`type`、`sort_order` |
| `tags` | 标签 | `name`、`slug` |
| `contents` | 内容元信息 | `title`、`slug`、`body_path`、`type`、`status`、`metadata` |
| `content_category` | 内容-分类关联 | `content_id`、`category_id` |
| `content_tag` | 内容-标签关联 | `content_id`、`tag_id` |
| `images` | 图片元信息 | `original_name`、`stored_name`、`path`、`size`、`content_type` |
| `site_options` | 站点配置 | `option_key`、`option_value` |

当前 DDL 已包含必要外键和级联约束，Service 仍负责返回可读的 409 业务错误。

### 3.3 内容类型

| 类型 | 用途 |
|---|---|
| `NOTE` | 笔记 |
| `NOVEL` | 小说章节 |
| `BOOK_REVIEW` | 读后感 |

状态只有：

| 状态 | 公开可见 |
|---|---|
| `DRAFT` | 否 |
| `PUBLISHED` | 是 |

---

## 4. 文件存储

存储根目录由 `app.storage-path` 指定，默认 `./data`。

Markdown：

```text
contents/NOTE/{slug}.md
contents/BOOK_REVIEW/{slug}.md
contents/NOVEL/{bookSlug}/{slug}.md
```

图片：

```text
images/{YYYY}/{MM}/{uuid}.{ext}
```

图片通过 `/images/**` 静态映射访问。Multipart 单文件和单请求限制均为 50MB。

---

## 5. API 与响应

API 分两组：

- 公开端：`/api/public/**`，共 8 个接口。
- 管理端：`/api/admin/**`，共 19 个接口；除登录外都经过 JWT 拦截器。

正常响应直接返回业务数据，例如：

```json
{
  "items": [],
  "page": 1,
  "size": 10,
  "total": 0
}
```

异常响应：

```json
{
  "code": 404,
  "message": "Content not found: no-such-slug"
}
```

完整接口见 [api-reference.md](api-reference.md)。

---

## 6. 关键业务流

### 6.1 文章读取

```text
公开请求
  -> ContentController
  -> ContentServiceImpl
  -> ContentMapper 查询 PUBLISHED
  -> 列表和详情批量组装分类/标签
  -> 从磁盘读取 Markdown
  -> 返回 ContentListVO / ContentDetailVO
```

### 6.2 文章写入

```text
管理请求
  -> ContentManageController
  -> ContentManageServiceImpl
  -> 写 Markdown
  -> 写 contents
  -> 重建分类/标签关联
  -> 返回详情
```

`@Transactional` 只覆盖数据库事务，文件系统操作不在事务中。

### 6.3 搜索

```text
GET /api/public/contents/search
  -> RateLimitInterceptor（同 IP 10 秒）
  -> SELECT ... WHERE status='PUBLISHED'
       AND (title LIKE OR summary LIKE)
```

当前不检索 Markdown 正文。

---

## 7. 安全与配置

| 机制 | 当前实现 |
|---|---|
| 管理认证 | `AdminInterceptor` 校验 Bearer JWT |
| 密码 | BCrypt |
| 搜索限流 | `ConcurrentHashMap` 内存计数 |
| CORS | 由 `CORS_ALLOWED_ORIGINS` 配置，开发默认 `http://localhost:5173` |
| 静默入口 | 前端由 `VITE_ADMIN_PATH` 控制，默认 `/secret-admin` |
| 可信代理 | `TRUSTED_PROXIES` 支持精确 IP/CIDR；Docker Nginx 直接代理默认信任专用 `/24` |
| JWT 密钥 | 环境变量 `JWT_SECRET`，默认占位值仅用于本地 |
| 管理员初始化 | `users` 表为空时由 `DataInitializer` 创建 |

---

## 8. 前端现状

前端已具备：

- 路由和管理端 Token 守卫。
- Axios 实例、JWT 自动附加和 401 跳转。
- 公开/管理 API 函数。
- 公开端首页、书库、搜索、详情、About、Project 和 404。
- 管理端登录、响应式布局、文章、分类、标签、站点设置和修改密码。
- 纯浏览器 Markdown 编辑器，支持 `.md` 导入/下载和本地草稿恢复。
- Markdown 渲染、代码高亮和图片拖入/粘贴上传。

当前版本已完成后端 API、公开阅读端、公开在线编辑器和全部管理端核心业务页。

---

## 9. 已知架构风险

1. 搜索和登录限流仍是单实例内存状态。
2. MySQL 8.4 Schema、种子、迁移和接口链已在 CI 自动化；历史 MySQL 5.7
   兼容迁移副本仍保留为人工演练证据。
3. 浏览器 E2E 使用 Mock API，Windows Chrome 与 Linux Chromium 视觉基线已接入 CI；
   浏览器测试不连接真实后端。
4. 图片没有删除接口，测试或误上传文件只能保留。

完整风险见 [codebase-memory.md](../project/codebase-memory.md) 和 [audit-log.md](../project/audit-log.md)。
