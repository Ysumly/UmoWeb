# UmoWeb 代码基线记忆

> 基线日期: 2026-09-10
> 范围: 当前工作区中的前端、后端、数据库脚本和文档
> 原则: 代码行为优先；计划能力与已实现能力必须分开记录

---

## 1. 仓库结构

```text
UmoWeb/
├── AGENT.md
├── AGENTS.md
├── CLAUDE.md
├── Client Side/
│   └── umo-web-frontend/
├── Server Side/
│   └── UmoWebBackend/
├── docs/
│   ├── design/
│   ├── modules/
│   └── project/
├── Downloads/                  # 敏感目录，禁止提交
└── .superpowers/
```

后端主源码为 80 个文件，前端 `src` 为 24 个文件。仓库当前只追踪少量文档；大量源码和文档仍是未提交状态。

---

## 2. 技术基线

### 2.1 后端

| 项 | 实际值 |
|---|---|
| 构建工具 | Maven，`com.ysumly:UmoWebBackend:0.0.1-SNAPSHOT` |
| Spring Boot | 4.1.0 |
| Java 编译版本 | 17 |
| Web | `spring-boot-starter-webmvc` |
| 参数校验 | `spring-boot-starter-validation` |
| ORM | MyBatis Spring Boot Starter 4.0.1 |
| 数据库 | MySQL，默认库 `umo_blog` |
| JWT | JJWT 0.12.6，默认 24 小时 |
| 密码 | `spring-security-crypto` + BCrypt |
| AI | Spring AI BOM 2.0.0-M4 + OpenAI Starter，当前无业务调用 |
| 测试 | Spring Boot Test、MyBatis Test、20 个 MockMvc 边界测试 |

### 2.2 前端

| 项 | 实际值 |
|---|---|
| 框架 | Vue 3.5.x，Composition API |
| 构建工具 | Vite 8.1.x |
| 路由 | Vue Router 5.1.x |
| 状态 | Pinia 3.0.x |
| HTTP | Axios 1.18.x |
| Markdown | marked 18.0.x、highlight.js 11.11.x |
| 样式 | Tailwind CSS 4.3.x |
| 编辑器 | 未安装 CodeMirror/Monaco |

---

## 3. 运行与验证

### 3.1 后端

```powershell
cd "Server Side\UmoWebBackend"
$env:DB_USER = "root"
$env:DB_PASS = "<password>"
$env:JWT_SECRET = "<secret>"
mvn spring-boot:run
```

测试：

```powershell
mvn test
```

若机器级 Maven `settings.xml` 的仓库路径不可写，应通过 `-gs` 和 `-s` 指向隔离的临时 settings 文件运行 Maven，不要修改系统安装目录。

说明：项目自带 `mvnw.cmd`，但在当前 Windows/PowerShell 环境中曾因 wrapper 脚本执行失败；系统 Maven 可用。2026-09-10 使用隔离临时 settings 执行 `mvn test`，21 个测试全部通过。

### 3.2 前端

```powershell
cd "Client Side\umo-web-frontend"
npm install
npm run dev
npm run build
```

Vite 开发端口为 5173，并将 `/api`、`/images` 代理到 `http://localhost:8080`。

---

## 4. 后端实现

### 4.1 包结构

```text
com.ysumly.umowebbackend/
├── UmoWebBackendApplication.java
├── common/
│   ├── constant/
│   ├── exception/
│   └── util/
├── config/
├── controller/
│   ├── open/
│   └── admin/
├── mapper/
├── model/
│   ├── dto/
│   ├── entity/
│   └── vo/
└── service/
    ├── open/
    ├── admin/
    └── impl/
        ├── open/
        └── admin/
```

实现数量：

| 层 | 数量 |
|---|---|
| Controller | 10（公开 4、管理 6） |
| Service 接口/实现 | 9/9 |
| Mapper 接口/XML | 8/8 |
| Entity | 6 |
| DTO | 8 |
| VO | 6 |
| Config | 5 |
| 边界测试 | 20 |

### 4.2 正常与异常响应

正常响应直接返回业务对象，不套统一外壳：

```json
{
  "siteTitle": "Umo Blog",
  "siteSubtitle": "代码 · 阅读 · 创作",
  "aboutHtml": "## 关于我",
  "projectHtml": "## 项目"
}
```

异常响应格式：

```json
{
  "code": 404,
  "message": "Content not found: no-such-slug"
}
```

HTTP 状态与返回：

| 场景 | 状态 |
|---|---|
| 查询、登录、新建、编辑成功 | 200 |
| 删除成功、修改密码成功、配置更新成功 | 204 |
| 参数校验/请求体错误 | 400 |
| 未认证/密码错误 | 401 |
| 资源不存在 | 404 |
| 唯一键冲突/删除受保护资源 | 409 |
| 上传超过 50MB | 413 |
| 搜索频率超限 | 429 |
| 未处理异常 | 500 |

### 4.3 API 清单

公开端共 8 个：

| 方法 | 路径 |
|---|---|
| GET | `/api/public/site-info` |
| GET | `/api/public/pages/about` |
| GET | `/api/public/pages/project` |
| GET | `/api/public/categories` |
| GET | `/api/public/tags` |
| GET | `/api/public/contents` |
| GET | `/api/public/contents/{slug}` |
| GET | `/api/public/contents/search` |

管理端共 19 个：

| 方法 | 路径 |
|---|---|
| POST | `/api/admin/login` |
| PUT | `/api/admin/change-password` |
| GET/POST | `/api/admin/contents` |
| GET/PUT/DELETE | `/api/admin/contents/{id}` |
| GET/POST | `/api/admin/categories` |
| GET/PUT/DELETE | `/api/admin/categories/{id}` |
| GET/POST | `/api/admin/tags` |
| PUT/DELETE | `/api/admin/tags/{id}` |
| POST | `/api/admin/images/upload` |
| GET | `/api/admin/options` |
| PUT | `/api/admin/options/{key}` |

### 4.4 查询语义

- `page` 默认 1，`size` 默认 10，当前没有范围校验。
- `sort` 仅当值严格等于 `created_at_desc` 时按创建时间倒序；其他值都按 `published_at DESC`。
- 公开列表和详情只处理 `status=PUBLISHED`。
- `categoryId` 使用 `EXISTS` 精确匹配关联分类，不包含子分类。
- `tagId` 精确匹配标签。
- 搜索 SQL 为 `title LIKE` 或 `summary LIKE`，不检索 Markdown 正文。
- `q` 为空或未传时，搜索等价于匹配全部已发布内容。
- 搜索同 IP 10 秒内只允许一次，内存计数，重启后清空。
- 公开文章列表会组装 `categories` 和 `tags`；公开详情当前不会，序列化结果为 `null`。

### 4.5 内容与文件

`contents.body_path` 的生成规则：

| type | 路径 |
|---|---|
| `NOTE` | `contents/NOTE/{slug}.md` |
| `BOOK_REVIEW` | `contents/BOOK_REVIEW/{slug}.md` |
| `NOVEL` | `contents/NOVEL/{bookSlug}/{slug}.md` |

当前 `NOVEL` 的 `bookSlug` 取自 `categoryIds` 的第一个分类 slug，不校验该分类是否为书级分类。未传分类时使用空字符串。

图片保存在：

```text
images/{YYYY}/{MM}/{uuid}.{ext}
```

上传校验 MIME 为 `image/jpeg`、`image/png`、`image/gif`、`image/webp`，Spring Multipart 限制单文件和请求均为 50MB。

### 4.6 认证与初始化

- `AdminInterceptor` 拦截 `/api/admin/**`，排除 `/api/admin/login`。
- JWT 放在 `Authorization: Bearer <token>`。
- JWT secret 经 SHA-256 后作为 HMAC key。
- `DataInitializer` 在 `users` 表为空时创建管理员，默认 `admin/admin123`。
- CORS 只允许 `http://localhost:5173`。

---

## 5. 数据库基线

实际 DDL 定义 8 张表：

| 表 | 用途 |
|---|---|
| `users` | 管理员账号 |
| `categories` | 分类层级 |
| `tags` | 标签 |
| `contents` | 内容元信息和 Markdown 路径 |
| `content_category` | 内容与分类关联 |
| `content_tag` | 内容与标签关联 |
| `images` | 图片元信息 |
| `site_options` | 站点 KV 配置 |

当前 SQL 没有外键约束，也没有级联删除；关联清理由 Service 手动完成。

---

## 6. 前端实现状态

### 6.1 已实现

- 路由表和管理端 Token 守卫。
- Axios 实例、JWT 注入、401 清理 token 并跳转登录页。
- 公开 API 封装和管理 API 封装。
- Pinia `auth` store。
- 部分 `site` store。
- 管理端登录页。
- 管理端布局和退出登录。
- 404 页面。

### 6.2 占位或未实现

- 首页、分类浏览、搜索、文章详情、About、Project、在线编辑器。
- 管理端文章列表、文章编辑、分类管理、标签管理、站点设置。
- Markdown 渲染和语法高亮尚未接入页面。
- 图片上传函数已封装，但编辑器尚未实现拖入/粘贴流程。
- 修改密码 API 函数、页面和入口缺失。
- `site` store 只有 `fetch()`，没有文档曾提到的 `fetchSiteInfo()`。
- 当前不存在 `AppHeader`、`AppFooter`、`ContentForm`、`MarkdownRenderer` 等公共组件。

前端页面文件共 14 个，其中 `LoginPage`、`AdminLayout` 和 `NotFoundPage` 有实际界面；其余业务页面基本是占位。

---

## 7. 测试与已知风险

### 7.1 当前测试覆盖

- `BoundaryTest` 使用独立 MockMvc 和 Mock Service，共 20 个用例。
- 覆盖参数错误、404、401、409 和部分接口状态码。
- `UmoWebApplicationTests` 是空测试，不加载完整 Spring 上下文。
- 没有真实 MySQL、文件系统、拦截器或前端自动化集成测试。

### 7.2 当前代码风险

| 级别 | 位置 | 事实 |
|---|---|---|
| 高 | `CategoryManageServiceImpl.delete` | 使用 `findCategoryIdsByContentId(categoryId)` 检查关联，查询方向错误，删除保护不能按设计工作。 |
| 高 | `TagManageServiceImpl.delete` | 同样把标签 ID 当作内容 ID 查询关联，删除保护不能按设计工作。 |
| 中 | `ContentServiceImpl.getBySlug` | 公开详情没有组装分类和标签，`categories`、`tags` 返回 `null`。 |
| 中 | `ContentManageServiceImpl.update` | 先删除旧 Markdown 再写新文件，文件系统与数据库事务不具原子性。 |
| 中 | `ContentManageServiceImpl.resolveBookSlug` | 小说书级目录来自第一个分类 ID，缺少分类层级校验。 |
| 中 | `ContentQuery` | `page`/`size` 没有边界校验，异常值可能造成 SQL 错误或异常分页。 |
| 中 | `RateLimitInterceptor` | IP 记录只增不减，且无条件信任 `X-Forwarded-For`。 |
| 中 | 数据库 | 无外键和级联约束，Service 漏操作会留下孤儿数据。 |
| 低 | `app.admin-path` | 配置存在，但前后端路由均未读取，管理入口实际硬编码。 |
| 低 | 爬虫控制 | `index.html` 有 `noindex`，但没有 `public/robots.txt`。 |

---

## 8. 文档维护规则

1. 接口路径、请求字段、响应结构和状态码以 Controller、DTO、VO、`GlobalExceptionHandler` 为准。
2. 查询行为以 Mapper XML 为准。
3. 文件路径以 `FileUtil` 和服务实现为准。
4. 前端能力以 `src` 中实际文件和 `package.json` 为准，不把设计目标写成现状。
5. 已知缺陷记录在审计日志，不伪装成预期功能。
6. 每次代码变更同步更新本文件、`api-reference.md` 和状态记录。
