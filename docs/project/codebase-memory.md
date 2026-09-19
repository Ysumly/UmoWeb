# UmoWeb 代码基线记忆

> 基线日期: 2026-09-19
> 范围: 当前工作区中的前端、后端、数据库脚本和文档
> 原则: 代码行为优先；计划能力与已实现能力必须分开记录

---

## 1. 仓库结构

```text
UmoWeb/
├── .github/
│   └── workflows/
├── AGENT.md
├── AGENTS.md
├── CLAUDE.md
├── compose.yaml
├── Client Side/
│   └── umo-web-frontend/
├── Server Side/
│   └── UmoWebBackend/
├── docker/
│   ├── backend/
│   ├── demo-data/
│   └── frontend/
├── docs/
│   ├── design/
│   ├── modules/
│   └── project/
├── scripts/
│   ├── ci/
│   └── docker-up.ps1
├── Downloads/                  # 敏感目录，禁止提交
└── .superpowers/
```

后端主源码为 148 个 Java 文件；前端 `src` 当前包含路由/API/store、真实公开端页面、
本地工具中心、游戏规则与页面、主题与 Markdown 工具和 Node 测试。`Downloads/`、
`.superpowers/`、`target/`、`dist/`、`node_modules/` 和真实 secret 继续排除。

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
| JSON | Jackson 3.1.4，Spring Boot 自动配置 `tools.jackson.databind.ObjectMapper` |
| AI | Spring `RestClient` + DeepSeek OpenAI-compatible；模式目录、转换运行时和文章 AI 抽屉已接入 |
| 测试 | Spring Boot Test、Mockito、MockMvc；238 个测试（17 个 MySQL 环境门控） |

### 2.2 前端

| 项 | 实际值 |
|---|---|
| 框架 | Vue 3.5.x，Composition API |
| 构建工具 | Vite 8.1.x |
| 路由 | Vue Router 5.1.x |
| 状态 | Pinia 3.0.x |
| HTTP | Axios 1.18.x |
| Markdown | marked 18.0.x、highlight.js 11.11.x、yaml 2.9.x |
| 样式 | Tailwind CSS 4.3.x |
| 编辑器 | 原生 textarea；未安装 CodeMirror/Monaco |
| 浏览器测试 | Playwright Test 1.63；Windows Chrome channel、Linux Chromium，Mock API |
| 容器构建 | Node 24.12 Alpine、Maven 3.9.11/JDK 17、JRE 17、Nginx 1.29 |

- 管理端 AI 已完成 5.1B 模式目录、5.1C 设置页、5.1D 转换运行时、5.1E 文章 AI 抽屉
  和 5.1F 自动/真实模型质量验收；正在完成发布门禁，入口见
  `docs/superpowers/plans/2026-09-18-admin-ai-index.md`。

### 2.3 持续集成

| 项 | 实际值 |
|---|---|
| 平台 | GitHub Actions，私有仓库 |
| 触发 | `pull_request` 和 `master` push |
| 运行环境 | Ubuntu、Temurin Java 17、Node 24.12.0 |
| 检查 | 后端 Maven 测试、MySQL 8.4 Schema/种子/迁移与接口冒烟、前端 Node 测试、前端构建、Linux Playwright、diff 检查和敏感信息扫描 |
| 视觉基线 | 34 张 Windows Chrome 与 34 张 Linux Chromium 独立 PNG |
| 权限 | `contents: read`，不配置仓库 Secret |
| 合并门禁 | 当前私有仓库计划不支持分支保护或规则集，失败结果不能强制阻止合并 |

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

说明：项目自带 `mvnw.cmd`，但在当前 Windows/PowerShell 环境中曾因 wrapper 脚本执行失败；系统 Maven 可用。2026-09-12 使用项目内 `.m2/repository` 隔离仓库执行 `mvn test`，83 个测试全部通过。

回填全文索引：

```bash
cd "Server Side/UmoWebBackend"
mvn -DskipTests package
bash ../../scripts/search/rebuild-content-search.sh
```

真实接口冒烟：

```powershell
cd "Server Side\UmoWebBackend"
.\scripts\api-smoke.ps1 -BaseUrl "http://127.0.0.1:8080" -Username "admin" -Password "<current-password>"
```

脚本覆盖 31 个接口，并校验管理端 401、搜索 429、详情前后文章、改密后旧 token 失效、
待发布隔离、批量文章操作、图片完整生命周期和图片一致性来源定位。

Docker 全栈：

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\docker-up.ps1
docker compose --env-file .env.docker ps
```

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
| Controller | 12（公开 4、管理 8） |
| Service 接口/实现 | 14 个接口 + 23 个 `service/impl` 实现或辅助类 |
| Mapper 接口/XML | 12/12 |
| Entity | 9 |
| DTO | 16 |
| VO | 23 |
| Config | 15 |
| 边界测试 | 45 |

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
| AI 上游请求或响应无效 | 502 |
| AI 上游认证、余额或服务不可用 | 503 |
| AI 上游响应超时 | 504 |
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

管理端共 32 个：

| 方法 | 路径 |
|---|---|
| POST | `/api/admin/login` |
| PUT | `/api/admin/change-password` |
| GET/POST | `/api/admin/contents` |
| GET/PUT/DELETE | `/api/admin/contents/{id}` |
| POST | `/api/admin/contents/bulk` |
| GET/POST | `/api/admin/categories` |
| GET/PUT/DELETE | `/api/admin/categories/{id}` |
| GET/POST | `/api/admin/tags` |
| PUT/DELETE | `/api/admin/tags/{id}` |
| POST | `/api/admin/images/upload` |
| GET | `/api/admin/images` |
| GET | `/api/admin/images/integrity` |
| DELETE | `/api/admin/images/{id}` |
| GET | `/api/admin/options` |
| PUT | `/api/admin/options/{key}` |
| GET | `/api/admin/ai/modes` |
| POST | `/api/admin/ai/modes` |
| POST | `/api/admin/ai/modes/{id}/copy` |
| PUT | `/api/admin/ai/modes/{id}` |
| GET | `/api/admin/ai/modes/{id}/versions` |
| POST | `/api/admin/ai/modes/{id}/rollback/{versionNo}` |
| GET | `/api/admin/ai/settings` |
| GET | `/api/admin/ai/capabilities` |
| POST | `/api/admin/ai/transform` |

### 4.4 查询语义

- `page` 默认 1 且限制为 1-1000000；`size` 默认 10 且限制为 1-100。
- `sort` 仅当值严格等于 `created_at_desc` 时按创建时间倒序；其他值都按 `published_at DESC`。
- 公开列表和详情只处理 `status=PUBLISHED`。
- `categoryId` 默认使用 `EXISTS` 精确匹配关联分类；`includeDescendants=true` 时先解析全部后代 ID，
  再使用 `IN` + `EXISTS` 过滤，内容不重复。
- `tagId` 精确匹配标签。
- 分类后代由 `CategoryHierarchyResolver` 展开；命中范围内循环或超过 32 层返回 409，
  分类不存在仍返回空结果。排序在时间字段后使用 `id DESC` 稳定次序。
- 搜索正文使用 `content_search` 的 MySQL 8.4 `ngram` FULLTEXT 索引，标题和摘要继续使用
  `LIKE` 子串匹配；正文命中时返回可选 `excerpt`。
- `q` 为空或未传时，搜索等价于匹配全部已发布内容。
- 搜索同 IP 10 秒内只允许一次；仅信任显式配置的代理，记录定期清理。
- 公开文章列表和详情都组装 `categories` 和 `tags`，使用共享 `ContentVOMapper` 按 contentIds 批量查询。
- 公开详情内联最多 4 篇 `related`，排除当前及前后篇，按共享标签 3 分、共享分类 2 分、
  同类型 1 分排序；查询失败时返回空数组，不影响正文。
- 内容生命周期为 `DRAFT`、`SCHEDULED`、`PUBLISHED`、`ARCHIVED`；公开接口固定只返回
  `PUBLISHED`，管理端返回全部状态和可选 `scheduledAt`。
- 公开详情返回 `previous` 和 `next` 摘要；前者为更早内容，后者为更新内容，同时间以小 ID 为更早。

### 4.5 AI 转换运行时

- `AiProperties` 从 `app.ai` 绑定开关、输入/输出上限、180 秒超时、5 次/10 分钟窗口、
  同时 1 请求和全局 DeepSeek 配置；`APP_AI_ENABLED=false` 时应用可正常启动。
- `DeepSeekAiTransformProvider` 通过 Spring `RestClient` 调用 `/chat/completions`，
  system prompt 与正文分开发送，`stream=false`，不自动重试。
- `AiRequestGuardImpl` 使用进程内 `Semaphore` 和时钟窗口；异常路径释放并发许可，
  单实例边界与现有搜索/登录限流一致。
- `AiResultValidatorImpl` 支持 `EXACT_CONTENT`、`TRANSLATION`、`LIGHT_EXPANSION` 和 `NONE`；
  `AiTransformServiceImpl` 负责模式状态、错误状态归一化、结果字段和脱敏元数据日志。
- 转换结果不自动写入文章；正文、结果、提示词和 API Key 不进入日志或数据库。

### 4.6 内容与文件

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

图片一致性检查：

- 严格扫描全部文章 Markdown 与 About/Project，返回内容 ID/标题或固定页来源。
- 对比 `images` 记录和 `app.storage-path/images` 普通文件，分别报告断裂引用、记录缺文件和磁盘孤立文件。
- 被内容引用但数据库无记录的磁盘文件只归入断裂引用；已有记录但未被引用仍保持 `ORPHANED`。
- 扫描失败返回 500，不返回部分报告；不自动修复、不持久化历史、不新增 Schema。

文章文件一致性：

- 创建拒绝重复 slug 和已存在文件，使用同目录临时文件且最终移动不覆盖。
- 更新先备份同路径旧文件，数据库成功后原子替换；路径变化在提交后删除旧文件。
- 数据库回滚时恢复旧内容或删除新文件；提交后清理失败会记录日志。
- 删除先提交数据库，再清理 Markdown；数据库失败不会丢文件。

### 4.7 认证与初始化

- `AdminInterceptor` 拦截 `/api/admin/**`，排除 `/api/admin/login`。
- JWT 放在 `Authorization: Bearer <token>`，包含 `ver` tokenVersion。
- JWT secret 经 SHA-256 后作为 HMAC key。
- `DataInitializer` 在 `users` 表为空时创建管理员，默认 `admin/admin123`。
- 默认 profile 为 `dev` 且明确允许默认凭据；`prod` 下默认 JWT secret 或管理员密码会阻止启动。
- 修改密码递增 `users.token_version`，旧 token 立即失效。
- 登录按规范化 username + client IP 在 15 分钟窗口内限制 5 次失败。
- CORS 来源由 `app.cors.allowed-origins` 配置，开发默认 `http://localhost:5173`。
- `app.security.trusted-proxies` 支持逗号分隔的精确 IP 或 CIDR，例如 `172.30.0.10/32`；非法配置启动失败。

---

## 5. 数据库基线

实际 DDL 定义 12 张表：

| 表 | 用途 |
|---|---|
| `users` | 管理员账号 |
| `categories` | 分类层级 |
| `tags` | 标签 |
| `contents` | 内容元信息和 Markdown 路径 |
| `content_category` | 内容与分类关联 |
| `content_tag` | 内容与标签关联 |
| `images` | 图片元信息 |
| `image_cleanup_queue` | 图片文件待清理与重试队列 |
| `content_search` | 已发布 Markdown 正文全文索引 |
| `site_options` | 站点 KV 配置 |
| `ai_transform_modes` | AI 转换模式元数据与当前版本 |
| `ai_transform_mode_versions` | 不可变 AI 提示词版本 |

当前 SQL 含必要索引和外键：

- 内容关联的分类/标签外键使用 `ON DELETE RESTRICT`，内容外键使用 `ON DELETE CASCADE`。
- `categories.parent_id` 使用自引用 `RESTRICT`。
- 旧库通过 `docs/design/migrations/20260911_integrity_security.sql` 兼容迁移。
- 图片清理队列通过 `docs/design/migrations/20260913_image_cleanup_queue.sql` 幂等迁移。
- 正文索引通过 `docs/design/migrations/20260913_content_search.sql` 幂等迁移。
- 定时发布通过 `docs/design/migrations/20260915_content_schedule.sql` 幂等新增
  `scheduled_at` 与 `(status, scheduled_at)` 索引。
- AI 模式目录通过 `docs/design/migrations/20260918_admin_ai_modes.sql` 幂等新增两张表、
  五个默认停用模式和 version 1。

---

## 6. 前端实现状态

### 6.1 已实现

- 路由表和管理端 Token 守卫。
- 公开路由、登录页和 404 仅在 `requiresAuth === true` 时校验 token。
- Axios 实例、JWT 注入、401 清理 token 并跳转登录页。
- 公开 API 封装和管理 API 封装。
- Pinia `auth` store。
- `site` store 缓存站点标题、副标题和加载错误；About/Project 页面各自读取专用接口。
- 管理端登录页。
- 管理端响应式布局、主题切换和退出登录。
- 管理端文章列表：四状态筛选、分页、当前页批量分类/标签、归档/恢复、状态展示、编辑和删除。
- 管理端文章列表与公开列表共享 `includeDescendants` 参数；书库选择分类时默认包含全部子分类，
  URL 同步 `category=<id>&includeDescendants=true`。
- 管理端文章编辑器：新建/编辑、分类标签单行选项与分页、metadata 校验、Markdown 分屏预览、
  可展开的 metadata 字段说明、图片选择/拖拽/粘贴、定时发布和未保存离开保护。新建页支持单文件
  `.md`/`.markdown` 导入，解析 YAML front matter，并在缺失时从 H1、文件名和默认值回退。
- 管理端分类管理：按类型筛选树形结构，支持父级、排序值、增改删和关联/子分类 409 提示；
  分类名与 slug 统一左对齐，使用固定标记表达父子层级，编辑加载态保持文案和列宽稳定。
- 管理端标签管理：列表增改删、字段校验和关联内容 409 提示。
- 管理端图片管理：缩略图列表、全部/使用中/未引用筛选、分页、引用状态和删除确认；
  被文章或固定页引用时显示 409 保护提示。
- 管理端 AI 设置：五个默认模式列表，支持新建、复制、编辑、快速启停、整数排序、
  历史提示词只读预览和回滚；提示词或校验策略变化才生成新版本，409 冲突保留本地表单并支持重新加载。
- 管理端文章 AI 抽屉：能力开启时在正文工具栏显示入口，支持正文带入、模式执行/取消、
  结果编辑/预览/复制、重新转换确认、浮动恢复和本地状态恢复；最多 20,000 字符。
  源草稿使用 `sessionStorage["umo-admin-ai-source-v1"]`，结果使用
  `localStorage["umo-admin-ai-result-v1"]`；源草稿只在用户执行转换时发送到后端和供应商，
  两者都不写数据库，结果不自动写入文章。预览禁用远程图片加载，进行中请求参与路由离开确认。
- 管理端站点设置：统一读取/保存四项配置，About/Project 支持 Markdown 预览、部分保存反馈和缓存刷新。
- 管理端修改密码：独立受保护页面；成功后清理本地 token，并在登录页提示重新登录。
- 公开在线 Markdown 编辑器：导入/下载 `.md`、实时安全预览、移动端编辑/预览切换和 `umo-editor-draft-v1` 本地草稿恢复。
- 公开隐私说明：`/privacy` 读取 `/privacy-config.json`，展示实际原始日志与匿名聚合保留期；
  配置不可读时不展示未经确认的天数。
- 公开端布局、页头页脚、主题切换和 `v-reveal` 滚动揭示指令。
- Markdown `h1` 由共享 `.markdown-body` 规则统一，公开文章页与管理端预览字号、字重和行高一致。
- 首页、书库、搜索、文章详情、About 和 Project 已接入真实公开 API。
- 2026-09-12 已导入 28 篇正式学习笔记，形成 18 个分类、22 个标签和 91 张本地图片；
  两篇导航索引不进入公开文章，About/Project 使用正式配置。
- 亮暗双主题，主题值写入 `data-theme` 并持久化到 `localStorage`。
- 基于服务端契约的书库筛选/分页、搜索 429 倒计时、Markdown 渲染和代码高亮。
- 搜索覆盖标题、摘要和 Markdown 正文；正文命中时结果卡片展示 `excerpt`。
- 404 页面采用公开端视觉布局。
- 公开端已有 14 个业务路由：首页、书库、搜索、文章详情、About、Project、工具中心、
  在线编辑器、隐私说明、游戏中心及四款训练游戏；另有 404 回退。
- 2026-09-13 已完成 Task 3.2：分类筛选默认保持精确匹配，显式 `includeDescendants=true`
  展开全部后代；书库分类入口默认启用该行为，真实 MySQL 集成测试覆盖根/子/孙和循环拒绝。
- 2026-09-13 已完成 Task 3.3：图片列表与删除、全部文章和固定页引用扫描、持久化文件
  清理队列及管理端图片管理页。
- 2026-09-13 已完成 Task 3.4：MySQL 8.4 ngram 正文索引、写入生命周期同步、可重复回填、
  标题/摘要/正文统一搜索和正文命中摘要。
- 2026-09-14 已完成 Task 3.5：首页、导航和页脚增加游戏入口；Stroop、倒背数字、
  扑克牌记忆和舒尔特迁移为 Vue 路由，保留原规则和三组旧 `localStorage` 成绩键；
  游戏路由即时进入，反馈等待与装饰动画已经压缩，并通过 320×568 至 1440×900、
  10×10 网格、10 位数字和 10 张牌的响应式回归。
- 2026-09-14 已修复 Stroop 五色显示判定：文字填充与五个按钮共用色源；黑色刺激在亮暗主题
  分别使用略暗、略亮于背景的描边，并过滤键盘自动重复。浏览器回归覆盖五色、双主题和长按。
- 2026-09-14 已审核 Post-v1 后续路线：第四阶段为非 AI 阅读与编辑效率，第五阶段为后置的
  管理端 AI，公开 AI 最后；不包含文章修订历史。AI 草稿和转换结果仅保存在浏览器本地，
  不进入数据库或独立备份。
- 2026-09-14 已完成 Task 4.1 的目录与阅读进度：文章详情从 Markdown 自动生成 H1-H3 目录，
  桌面侧栏和窄屏悬浮面板均支持分支展开与当前/祖先高亮，阅读进度按正文滚动范围计算；
  标题使用稳定 ID 和旧版兼容别名，旧内容迁移脚本支持 dry-run、备份和原子写入。
  2026-09-15 已补齐相关阅读，正文内检索明确由浏览器原生查找承担，不提供站内搜索控件。
- 2026-09-17 已完成 Task 4.4：桌面文章目录始终保留在 sticky 侧栏，最高 `50vh` 并内部滚动，
  `980px` 及以下切换为可关闭的悬浮目录，不再按目录自然高度切换桌面抽屉；公开编辑器、
  管理端文章编辑器和 About/Project 设置编辑器等高，并通过 `useSyncedScroll` 在桌面按比例
  双向同步滚动，移动端单面板不执行同步；`body` 横向裁剪改用 `clip`，避免破坏页面滚动与
  sticky 定位。Markdown 渲染兼容加粗结束符后紧接正文的写法，无序列表中的加粗不再显示为星号。
- 2026-09-17 已新增本地工具中心：公开导航抽为共享配置，页头、移动菜单和页脚均可进入
  `/tools`；工具中心和在线编辑器共享 `section: tools` 激活态，首页增加独立工具模块，
  当前目录只包含 Markdown 编辑器。
- 2026-09-15 已完成 Task 4.2 图片一致性检查：图片管理页可按需展示三类异常、来源、计数和扫描时间，
  删除图片后旧报告失效；结果不写入 Pinia 或浏览器存储。
- 2026-09-15 已完成 Task 4.3 批量管理与定时发布：新增四种内容状态、当前页批量分类/标签、
  归档/恢复、未来的 `scheduledAt`、30 秒到期扫描、条件更新幂等保护和正文索引同步。
- 2026-09-19 已修复发布前移动端与界面缺陷：管理侧栏关闭后不再进入焦点、打开时限制 Tab 并支持
  Escape/焦点恢复、低高度横屏可内部滚动；公开与管理端当前页导航可关闭菜单；隐藏文件输入移出
  Tab 顺序；图片缩略图加载失败显示无障碍占位。AI 设置模式目录改为完整可点击卡片，并补充
  模式说明、系统提示词用途和校验策略展开解释；启停状态和操作分区固定，按钮/历史版本位于
  左栏。管理端文章编辑器默认在首尾补齐的标题锚点之间连续插值，双向同步且无开关；正文或宽度
  变化后刷新测量缓存，无可用标题或标题数不一致时回退整体比例。

### 6.2 其他前端事实

- 管理端路径由统一 `VITE_ADMIN_PATH` 工具控制，默认 `/secret-admin`，不再依赖后端 `app.admin-path`。
- 当前存在公开端 `SiteHeader`、`SiteFooter`、`ContentCard`、`ContentState`、`MarkdownArticle`、
  `ThemeToggle`、`GameShell`、`GameResultDialog` 和 `ToolsPage`；公开导航由
  `publicNavigation` 统一提供，编辑滚动由 `useSyncedScroll` 复用；管理端仍没有统一表单/表格组件。
- 公开端页头和管理端侧栏共用 `public/umo-logo.png`，浏览器图标为 `public/favicon.png`。
- 前端已建立 Playwright functional 与视觉回归；Windows 使用本机 Chrome，CI 使用 Linux Chromium，
  浏览器测试不连接后端，真实接口由 CI MySQL 8.4 集成 job 和冒烟脚本负责。

公开端主路径、在线编辑器和全部管理端核心业务页均已实现。

### 6.3 Docker 全栈

- 根目录 `compose.yaml` 启动 MySQL 8.4、Spring Boot 后端和 Nginx 前端。
- 仅前端映射宿主端口，默认 `8080`；MySQL 与后端只在 Compose 网络内访问。
- `mysql_data` 持久化数据库，`app_data` 持久化 Markdown 和上传图片。
- MySQL 空数据卷首次启动执行 `docs/design/schema.sql` 与 `docs/design/seed-data.sql`。
- 两个数据库脚本均显式 `SET NAMES utf8mb4`；内容种子重复执行时会刷新完整字段，避免容器初始化中文乱码。
- 后端镜像包含 6 篇演示 Markdown，公开端返回 5 篇已发布内容，管理端可见 1 篇草稿。
- `.env.docker` 由 `scripts/docker-up.ps1` 自动生成并被 Git 忽略，示例见 `.env.docker.example`。
- Nginx 代理 `/api/**` 和 `/images/**`，SPA 路由回退到 `index.html`，上传请求上限 52MB。
- Docker 默认网络为 `172.30.0.0/24`，前端固定为 `172.30.0.10`，后端只信任该地址 `/32` 转发的 `X-Forwarded-For`。
- Nginx 使用 JSON 六字段访问日志：客户端 IP、ISO 8601 时间、HTTP 方法、无查询字符串的真实路径、
  状态码和响应字节数；日志写入 `access/logs` 绑定目录，不记录请求体、Cookie、Authorization、
  Referer 或 User-Agent。
- `scripts/access/` 提供可信代理配置、每日轮转、7–30 天原始日志清理、180 天默认匿名聚合、
  零依赖 HTML 报表和 systemd 安装入口。报表包含保留期内 IP，只写入管理员权限目录并由
  专用非 root 服务监听 `127.0.0.1`。

### 6.4 阿里云 ECS 公网测试部署

- 2026-09-12 已在单台阿里云 ECS 完成无域名部署，系统为 Ubuntu 24.04，部署目录为
  `/opt/umoweb`，入口为 Nginx 宿主 TCP 80。
- 2026-09-12 已冻结 v1 正式发布范围，明确当前 ECS 原地升级路径、发布职责、阻断条件、
  回滚触发条件和第一阶段排除项；清单见 `docs/project/release-checklist-v1.md`。
- 2026-09-12 已建立 MySQL 与 `app_data` 一致性备份、校验、导出和隔离恢复链路；ECS 每周日
  03:30 自动执行，保留最多 6 份且不超过 8GiB；2026-09-14 修复版本化镜像标签和迁移前
  Schema 兼容后，发布前备份与 timer 均重新验证通过。
- 2026-09-12 已完成 Task 1.3：正式内容和文件替换演示数据，数据库密码、JWT secret 和管理员
  凭据均已轮换；生产与最终备份恢复环境均通过 27/27 冒烟。
- 该 ECS 访问 Docker Hub、npm 官方仓库和 Maven Central 受限；实际部署采用开发机构建镜像、
  校验归档后传输并在 ECS `docker load`，再执行 `compose up -d --no-build --wait`。
- 2026-09-13 已实现 `scripts/release/` 本地版本化发布/回滚入口和 ECS 控制脚本：
  发布要求当前 commit 存在成功 push CI，开发机保留最近两个镜像归档，ECS 只保留当前运行镜像、
  `current.json` 和发布期间上传目录。
- 2026-09-13 已完成真实发布/回滚演练：候选 `v1.0.0-rc.1` 由提交
  `f6f5ce170b3c` 构建，发布 CI run `34739475146`；随后回滚 `baseline-20260913`，
  再从开发机归档恢复 rc.1。生产最终运行 rc.1，前后端 image ID 与 manifest 一致。
- 安全组与 UFW 均放行 TCP 80；MySQL 3306 和后端 8080 没有暴露到公网。
- 2026-09-12 已验证首页、公开 API、管理员登录和公网访问；三个容器均为 healthy，
  Docker 服务已设置开机自启。
- 当前是公网测试部署，使用正式内容，没有域名或 HTTPS；自动备份和正式数据恢复演练已完成，
  基础 CI、Linux Playwright、真实 MySQL 集成、本地镜像发布/回滚和自托管访问统计均已接入。
- 2026-09-13 已完成 Task 2.5 的 `v1.0.0-rc.3` 发布：六字段日志、30 天原始保留、180 天匿名
  聚合、127.0.0.1 报表、公开隐私配置和 ECS 27/27 接口冒烟全部通过。
- 发布流程会同步 `compose.yaml` 和 `scripts/access/`；前端镜像入口使用 `umask 0027`，
  ECS 安装脚本维护日志目录、脚本可读性和日志重开，避免容器轮转后停止写入。
- 2026-09-14 起发布流程还会同步版本化 SQL 迁移和便携接口冒烟脚本；部署在切换镜像前执行
  幂等迁移，切换后回填正文索引并校验索引行数与 `PUBLISHED` 内容数一致，失败会恢复旧镜像。
- 2026-09-14 已发布 `v1.0.0-rc.4`：提交 `c9c9f8ee50e5`，发布 CI run `34807582609`；
  Task3 迁移后 `content_search=28`、`image_cleanup_queue=0`，ECS 29/29 和核心路由全部通过。
- 2026-09-14 已发布 `v1.0.0-rc.5`：提交 `2019c7521844`，发布 CI run `34813314026`；
  独立 `Verify` 和 ECS 29/29 通过。开发机保留 rc.4 与 rc.5，rc.4 为当前回滚目标。
- 2026-09-17 已发布 `v1.0.0-rc.6`：提交 `67b3c9f866d4`，发布 CI run `35183870810`；
  Phase 4 全部能力通过独立 `Verify` 和 ECS 31/31 真实接口冒烟。发布脚本的一次性正文回填
  显式关闭调度器后正常退出；该版本随后由 rc.7 替代，当时回滚目标为 `v1.0.0-rc.5`。
- 2026-09-17 已发布 `v1.0.0-rc.7`：提交 `eace1351adc8`，发布 CI run `35225558649`；
  Task 4.4 工具中心、共享导航、编辑器滚动协同和目录抽屉修复通过独立 `Verify`、
  ECS 31/31 真实接口冒烟及全部公开路由检查。该版本随后由 rc.8 替代，当时回滚目标为
  `v1.0.0-rc.6`。
- 2026-09-17 已发布 `v1.0.0-rc.8`：提交 `12bc8111cf36`，发布 CI run `35231477064`；
  桌面目录常驻与邻接 Markdown 加粗修复通过独立 `Verify`、ECS 31/31 和公网浏览器验证。
  该候选随后提升为正式版，当时回滚目标为 `v1.0.0-rc.7`。
- 2026-09-17 已发布正式版 `v1.0.0`：提交 `fe008c59cb71`，发布 CI run `35232962034`；
  正式版镜像、独立 `Verify`、ECS 31/31、公网目录/加粗回归和访问服务检查均通过。
  当前生产版本为 `v1.0.0`，正式回滚目标为 `v1.0.0-rc.8`。
- 实例标识、公网地址、随机管理路径、数据库密码、JWT secret 和管理员密码只保存在服务器侧，
  不进入版本库。
- 开发机已安装阿里云 Workbench CLI v1.0.1，绝对路径为
  `C:\Program Files\workbench\workbench.exe`；当前 `PATH` 不包含该目录，远程 ECS 操作必须使用
  完整路径。凭据配置位于用户目录的 `.workbench/config.json`，内容不进入版本库。

### 6.5 备份与恢复

- `scripts/backup/` 提供 Bash 备份、校验、导出、隔离恢复、清理和 systemd 安装入口。
- 备份时短暂停止 `frontend` 和 `backend`，通过 trap 恢复服务；MySQL 使用逻辑 dump，
  `app_data` 保存为压缩包并附带逐文件 SHA-256 清单。
- 备份脚本从 `.env.docker` 读取当前前后端版本标签，不再固定使用可能已被发布流程清理的
  `:latest` 镜像；发布入口同步备份控制文件并刷新 timer。迁移前进度中不存在的
  `image_cleanup_queue` 通过 `information_schema` 条件统计，不阻断旧 Schema 备份。
- Compose 后端和前端显式使用 `BACKEND_IMAGE`、`FRONTEND_IMAGE`，隔离项目无需重新构建镜像。
- 恢复项目只允许 `umoweb-restore-*`，使用独立卷、网络和回环端口，不覆盖生产 `umoweb`。
- 恢复数据库后自动补跑兼容迁移，并校验包含 `image_cleanup_queue` 在内的当前表行数。
- ECS systemd timer 每周日 03:30 执行，允许 10 分钟随机延迟并支持补跑；本地文件和目录权限为
  `0600/0700`。
- 当前归档不做加密和自动异地复制，只提供导出入口；正式数据导入后的最终恢复演练已完成。
- `scripts/content-import/` 提供正式内容候选包生成、生产提升和便携接口冒烟入口。候选包使用
  `umoweb-content-*` 命名，恢复前校验内外层 SHA-256，提升脚本必须显式传入 `--confirm`。
- `catalog.json` 为全部 28 篇正文保存人工摘要；构建器优先使用显式摘要，仅在缺失时回退到正文提取。

---

## 7. 测试与已知风险

### 7.1 当前测试覆盖

- `BoundaryTest` 使用独立 MockMvc 和 Mock Service，覆盖参数错误、404、401、409 和接口状态码。
- 新增文件路径/事务、上传签名、JWT/tokenVersion、登录限流、可信代理、VO 批量组装和安全配置测试。
- 新增文章创建成功时数据库记录、Markdown 文件、分类和标签关联同时写入的回归测试。
- 新增 Mapper XML 别名解析、`ClientIpResolver` 容器装配和 Jackson 3 自动配置回归测试。
- `ClientIpResolver` 支持精确 IP 与 IPv4/IPv6 CIDR，覆盖非法配置、可信代理链和未授权转发头。
- `UmoWebApplicationTests` 是空测试，不加载完整 Spring 上下文。
- 新增正文索引 upsert/剔除、回填容错、摘要提取、正文命中搜索、空查询契约和相关文章排序测试。
- 新增状态转换、定时时间校验、批量原子预检、分类/标签幂等更新、归档索引剔除和
  调度条件更新/失败重试测试。
- 新增 AI 模式创建、复制、元数据更新、提示词版本递增、10 版保留、回滚和乐观锁测试；
  MySQL 门控测试覆盖默认模式、停用过滤、条件版本更新和级联删除。
- 新增 DeepSeek Mock HTTP 请求/响应、错误分类、超时、请求窗口/并发、四类结果校验、
  转换错误映射和脱敏日志测试。
- 2026-09-13 已在 CI 使用 MySQL 8.4 从空库执行 Schema、种子数据和迁移幂等验证，启动真实后端并完成接口冒烟；2026-09-11 MySQL 5.7 迁移副本记录继续保留。
- PowerShell 与 Bash 发布脚本自测已纳入 `repository` CI job，覆盖 CI 选择、manifest、归档校验、
  发布锁、健康解析、失败自动回滚和版本基线捕获；真实 ECS 发布/回滚链路仍待演练。
- 内容导入与阅读锚点工具共有 10 个 Python 单元测试，覆盖标题/摘要、目录映射、内链、
  图片重写、内容去重、Linux 文件所有权、缺失素材阻断，以及旧锚点 dry-run、备份、
  原子写入、幂等和缺少目标阻断；`api-smoke.py` 与 PowerShell 版本覆盖同样的 31 个接口。
- 前端 126 个 Node 测试覆盖路由、管理路径、主题解析、隐私配置、游戏规则与旧成绩解析、
  管理端文章/分类/标签/图片/站点/改密表单规则、API 错误解析、编辑器草稿与文件规则、
  Markdown front matter 导入、日期格式、书库后代参数、目录树与展开状态、标题 ID/别名、
  AI 模式表单与版本载荷、AI 抽屉本地状态/字符边界、Markdown 原始 HTML、邻接正文的加粗、
  危险 URL 协议、图片 alt 转义、定时状态、编辑器滚动比例、标题锚点插值和批量载荷规则。
- Playwright 每个平台运行 120 个浏览器检查：86 个 functional 用例覆盖公开端、正文摘要、
  文章目录/阅读进度/相关阅读、图片一致性报告与竞态、隐私说明、工具中心、在线编辑器、
  三处编辑工作区双向滚动、标题区间单调插值与缓存失效、批量文章操作、定时发布、桌面目录常驻/侧栏内滚动/窄屏断点、
  四款游戏的高密度/长序列/旧成绩兼容、AI 模式卡片与策略说明、AI 转换抽屉、移动菜单焦点/横屏滚动、
  隐藏文件输入和管理端核心流程；
  34 个视觉断言覆盖 17 个核心页面状态的 `1440×900` 与 `390×844` 基线。
- 访问链路新增 9 个 Python 测试和 Nginx 容器集成测试，覆盖六字段白名单、查询参数和凭据剔除、
  IPv4/IPv6 聚合、保留边界、可信代理生成、报表转义和回环访问。
- Playwright 使用 `/api/**` Mock 路由和 `e2e/runPlaywright.js` 静态服务器，不依赖 MySQL；
  Windows 默认 Chrome channel，Linux CI 使用锁定 Playwright 版本的 Chromium。
- 仓库分别保存 34 张 `win32` 和 34 张 `linux` 视觉快照；Linux 快照通过手动
  `Playwright Linux Baselines` 工作流生成 artifact 后人工审查提交，不会自动写回仓库。
- `scripts/ci/scan-sensitive-info.sh` 扫描全部已跟踪文件，覆盖公开 IPv4、ECS 实例 ID、AccessKey、
  GitHub Token、JWT 形态、私钥头和误提交环境文件；对应 Bash 自测覆盖允许与拒绝场景。
- GitHub Actions 在 PR 和 `master` push 时运行仓库检查、后端测试、MySQL 8.4 集成、
  前端测试、生产构建和 Linux Playwright；MySQL job 同时验证 Schema、种子、两次正文回填、
  中文 ngram 查询、AI 模式 Mapper 和启用假供应商的 40/40 AI 契约冒烟。

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
| 已修复 | 上下文启动 | MyBatis 同时扫描 entity/dto 别名；`ClientIpResolver` 显式构造注入；业务 JSON 统一使用 Jackson 3。 |
| 已修复 | 内容导入 | 候选归档显式使用 `umo:umo` 文件所有权；恢复项目名限制为小写；管理员初始化完成后才轮换密码。 |
| 已修复 | CI 合并门禁 | 私有仓库当前计划不支持分支保护或规则集，CI 失败只能报告；本地发布入口已强制要求当前 commit 的成功 push CI，并完成真实发布/回滚演练。 |
| 低 | 图片引用扫描 | 列表、删除和一致性检查会读取全部文章正文与图片目录；当前规模可接受，内容量显著增长后应改为显式引用索引。 |
| 低 | 图片清理重试 | 清理队列只在启动和后续图片操作时重试；长期无图片操作时失败任务会等待下一次触发。 |
| 低 | 正文索引 | 直接改动 Markdown 文件不会自动更新索引，需要执行可重复的回填脚本。 |
| 低 | 相关文章排序 | 每次公开详情额外执行一次关联加权查询并读取相关文章分类/标签；当前规模可接受，内容量显著增长后应复评查询计划。 |
| 低 | 爬虫控制 | `index.html` 有 `noindex`，但没有 `public/robots.txt`。 |

---

## 8. 文档维护规则

1. 接口路径、请求字段、响应结构和状态码以 Controller、DTO、VO、`GlobalExceptionHandler` 为准。
2. 查询行为以 Mapper XML 为准。
3. 文件路径以 `FileUtil` 和服务实现为准。
4. 前端能力以 `src` 中实际文件和 `package.json` 为准，不把设计目标写成现状。
5. 已知缺陷记录在审计日志，不伪装成预期功能。
6. 每次代码变更同步更新本文件、`api-reference.md` 和状态记录。
