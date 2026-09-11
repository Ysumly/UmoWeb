# UmoWeb 代码基线记忆

> 基线日期: 2026-09-11
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

后端主源码为 80 个 Java 文件，前端 `src` 包含 27 个源码/测试文件。
本次修复会把必要的前后端源码、配置和测试纳入 Git；`Downloads/`、`.superpowers/`、
`target/`、`dist/`、`node_modules/` 和真实 secret 继续排除。

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
| 测试 | Spring Boot Test、Mockito、MockMvc；72 个测试 |

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

- `page` 默认 1 且限制为 1-1000000；`size` 默认 10 且限制为 1-100。
- `sort` 仅当值严格等于 `created_at_desc` 时按创建时间倒序；其他值都按 `published_at DESC`。
- 公开列表和详情只处理 `status=PUBLISHED`。
- `categoryId` 使用 `EXISTS` 精确匹配关联分类，不包含子分类。
- `tagId` 精确匹配标签。
- 搜索 SQL 为 `title LIKE` 或 `summary LIKE`，不检索 Markdown 正文。
- `q` 为空或未传时，搜索等价于匹配全部已发布内容。
- 搜索同 IP 10 秒内只允许一次；仅信任显式配置的代理，记录定期清理。
- 公开文章列表和详情都组装 `categories` 和 `tags`，使用共享 `ContentVOMapper` 按 contentIds 批量查询。

### 4.5 内容与文件

`contents.body_path` 的生成规则：

| type | 路径 |
|---|---|
| `NOTE` | `contents/NOTE/{slug}.md` |
| `BOOK_REVIEW` | `contents/BOOK_REVIEW/{slug}.md` |
| `NOVEL` | `contents/NOVEL/{bookSlug}/{slug}.md` |

当前 `NOVEL` 的 `bookSlug` 取自 `categoryIds` 的第一个分类 slug，并要求该分类存在且
`type=NOVEL`；未传有效分类时返回 400。

图片保存在：

```text
images/{YYYY}/{MM}/{uuid}.{ext}
```

上传同时校验 MIME 和文件签名，并只根据 MIME 映射固定扩展名。原始文件名只作为安全化后的展示元信息。
Spring Multipart 限制单文件和请求均为 50MB。

文章文件一致性：

- 创建拒绝重复 slug 和已存在文件，使用同目录临时文件且最终移动不覆盖。
- 更新先备份同路径旧文件，数据库成功后原子替换；路径变化在提交后删除旧文件。
- 数据库回滚时恢复旧内容或删除新文件；提交后清理失败会记录日志。
- 删除先提交数据库，再清理 Markdown；数据库失败不会丢文件。

### 4.6 认证与初始化

- `AdminInterceptor` 拦截 `/api/admin/**`，排除 `/api/admin/login`。
- JWT 放在 `Authorization: Bearer <token>`，包含 `ver` tokenVersion。
- JWT secret 经 SHA-256 后作为 HMAC key。
- `DataInitializer` 在 `users` 表为空时创建管理员，默认 `admin/admin123`。
- 默认 profile 为 `dev` 且明确允许默认凭据；`prod` 下默认 JWT secret 或管理员密码会阻止启动。
- 修改密码递增 `users.token_version`，旧 token 立即失效。
- 登录按规范化 username + client IP 在 15 分钟窗口内限制 5 次失败。
- CORS 来源由 `app.cors.allowed-origins` 配置，开发默认 `http://localhost:5173`。

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

当前 SQL 含必要索引和外键：

- 内容关联的分类/标签外键使用 `ON DELETE RESTRICT`，内容外键使用 `ON DELETE CASCADE`。
- `categories.parent_id` 使用自引用 `RESTRICT`。
- 旧库通过 `docs/design/migrations/20260911_integrity_security.sql` 兼容迁移。

---

## 6. 前端实现状态

### 6.1 已实现

- 路由表和管理端 Token 守卫。
- 公开路由、登录页和 404 仅在 `requiresAuth === true` 时校验 token。
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
- 修改密码 API 函数已补充；页面和入口仍缺失。
- 管理端路径由统一 `VITE_ADMIN_PATH` 工具控制，默认 `/secret-admin`，不再依赖后端 `app.admin-path`。
- `site` store 只有 `fetch()`，没有文档曾提到的 `fetchSiteInfo()`。
- 当前不存在 `AppHeader`、`AppFooter`、`ContentForm`、`MarkdownRenderer` 等公共组件。

前端页面文件共 14 个，其中 `LoginPage`、`AdminLayout` 和 `NotFoundPage` 有实际界面；其余业务页面基本是占位。

---

## 7. 测试与已知风险

### 7.1 当前测试覆盖

- `BoundaryTest` 使用独立 MockMvc 和 Mock Service，覆盖参数错误、404、401、409 和接口状态码。
- 新增文件路径/事务、上传签名、JWT/tokenVersion、登录限流、可信代理、VO 批量组装和安全配置测试。
- `UmoWebApplicationTests` 是空测试，不加载完整 Spring 上下文。
- 当前没有真实 MySQL 集成测试；前端路由使用 Node 内置测试覆盖，未做浏览器 E2E。

### 7.2 当前代码风险

| 级别 | 位置 | 事实 |
|---|---|---|
| 已修复 | 分类/标签删除 | 按 categoryId/tagId 统计关联，并阻止删除有子分类的父分类。 |
| 已修复 | 公开详情 | `categories`、`tags` 返回数组。 |
| 已修复 | 文件事务 | 创建、更新、删除采用临时文件、回滚恢复和提交后清理策略。 |
| 已修复 | 路径与上传 | slug/path normalize、MIME+签名、固定扩展名。 |
| 已修复 | 分页与输入 | page/size、枚举、metadata、长度和 slug 校验。 |
| 已修复 | 限流 | 可信代理、原子窗口更新、过期清理。 |
| 已修复 | 数据库完整性 | 外键、级联策略和兼容迁移脚本。 |
| 已修复 | 管理路径 | 前端 `VITE_ADMIN_PATH`，后端移除未使用配置。 |
| 低 | 爬虫控制 | `index.html` 有 `noindex`，但没有 `public/robots.txt`。 |

---

## 8. 文档维护规则

1. 接口路径、请求字段、响应结构和状态码以 Controller、DTO、VO、`GlobalExceptionHandler` 为准。
2. 查询行为以 Mapper XML 为准。
3. 文件路径以 `FileUtil` 和服务实现为准。
4. 前端能力以 `src` 中实际文件和 `package.json` 为准，不把设计目标写成现状。
5. 已知缺陷记录在审计日志，不伪装成预期功能。
6. 每次代码变更同步更新本文件、`api-reference.md` 和状态记录。
