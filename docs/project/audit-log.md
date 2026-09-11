# 审计日志

## 审计 #6 - 2026-09-11 — 公开端真实 API 闭环

### 范围

- 后端公开详情前后文章契约。
- 前端首页、书库、搜索、详情、About、Project 的公开 API 联调。
- 加载、空数据、错误、404、429、离线无兜底和响应式验证。

### 实现

- `ContentDetailVO` 新增 `previous`、`next`；邻居只含 `id/title/slug/publishedAt`。
- `ContentMapper` 新增两个定点查询，只处理 `PUBLISHED`，按发布时间和 ID 稳定排序。
- 前端移除 `src/demo/content.js`、`src/demo/catalog.js` 及其测试，新增查询规范化、错误解析、日期格式和公共状态组件。
- 书库由后端执行精确筛选和分页；搜索显式提交并处理 429；详情直接渲染后端前后文章。
- 首页并发读取最新内容与三类总数；About/Project 分别读取配置页接口。

### 验证

| 验证 | 结果 |
|---|---|
| 后端完整 `mvn test` | 通过，77 tests / 0 failures / 0 errors |
| 前端 `npm test` | 通过，21 tests / 0 failures |
| 前端 `npm run build` | Vite 8.1.0 生产构建通过 |
| 隔离 MySQL 5.7 + 临时 Markdown 存储 | schema/seed 初始化通过 |
| `api-smoke.ps1` | 通过，27/27；详情邻居顺序通过 |
| 桌面浏览器 | 首页、筛选、搜索 429、详情导航、About、Project、404 通过 |
| 390px 浏览器 | 首页、书库、详情无横向溢出，移动导航通过 |
| 后端离线 | API 错误态和重试可见，无静态 fixture 回退 |

### 剩余风险

1. 浏览器验证仍为手工编排，不是可重复执行的 E2E 工程。
2. 详情为两个额外邻居查询，当前公告规模可接受；数据量显著增长后可再评估合并 SQL。
3. 搜索/登录限流仍是单实例内存状态。
4. 在线编辑器和管理端业务页仍未实现。

## 审计 #5 - 2026-09-11 — 公开端静态视觉 MVP

### 范围

- 前端公开端布局、首页、书库、文章详情、About 和 404。
- 双主题、静态 fixtures、Markdown/代码高亮和公开端动效。
- 前端 Node 测试、生产构建及桌面/移动浏览器检查。

### 实现

- 新增“当代古籍纸本”视觉 tokens、公开端页头页脚、内容卡片和主题切换。
- 新增 `light | dark` 双主题，写入 `data-theme` 并持久化到 `umo-theme`。
- 首页、书库、文章详情、About 和 404 使用静态数据完成视觉 MVP。
- 书库实现类型、分类、标签的本地筛选和分页，URL 同步筛选参数。
- Markdown 禁用原始 HTML，代码高亮按需注册 Java、JavaScript、SQL 和 Bash。
- 公开展示页支持电影化入场与滚动揭示；正文与减少动态偏好使用克制版本。
- 路由切换和主题色幕布退场统一为水平方向，并根据主路径层级区分前进与返回。

### 修正

| # | 级别 | 问题 | 修复 |
|---|---|---|---|
| 1 | P2 | 书库内容卡片默认 `opacity: 0`，不是所有布局都会触发首页入场动画，导致结果卡片不可见。 | 为书库卡片增加 `v-reveal`，由 IntersectionObserver 在进入视口时揭示。 |
| 2 | P3 | 完整 `highlight.js` 导入产生约 956 KB chunk 和构建体积警告。 | 改用 `highlight.js/lib/core` 并注册当前使用的语言，chunk 降至约 80 KB。 |

### 验证

| 验证 | 结果 |
|---|---|
| 前端 `npm test` | 通过，15 tests / 0 failures |
| 前端 `npm run build` | 通过，无 chunk 体积警告 |
| 桌面浏览器 | 首页、书库筛选、文章详情、About、404 和亮暗主题通过 |
| 390px 浏览器 | 首页、移动导航、书库卡片、文章详情、About 和 404 通过 |

### 剩余风险

1. 当前仅使用静态 fixtures，尚未接入现有 8 个公开 API。
2. 搜索、Project、在线编辑器和管理端仍为占位或原有实现。
3. 尚未建立可重复执行的浏览器 E2E 和视觉回归。

## 审计 #1 - 2026-09-10

### 范围

- 后端 Controller、DTO、VO、Service、Mapper XML、Config。
- 前端路由、API、Store、页面和 `package.json`。
- `docs/` 下全部文档。
- 后端边界测试和前端生产构建。

### 发现

| # | 严重度 | 类别 | 文件/模块 | 描述 | 状态 |
|---|---|---|---|---|---|
| 1 | 高 | 数据完整性 | `CategoryManageServiceImpl.delete` | 把分类 ID 当作内容 ID 调用 `findCategoryIdsByContentId`，分类删除保护不能可靠工作。 | 审计 #2 已修复 |
| 2 | 高 | 数据完整性 | `TagManageServiceImpl.delete` | 把标签 ID 当作内容 ID 调用 `findTagIdsByContentId`，标签删除保护不能可靠工作。 | 审计 #2 已修复 |
| 3 | 高 | 文件安全 | `FileUtil` + 内容保存 | `slug`、`bookSlug` 未检查路径穿越，最终路径可能越出 storage root。 | 审计 #2 已修复 |
| 4 | 中 | 响应语义 | `ContentServiceImpl.getBySlug` | 公开详情未组装分类和标签，接口实际返回 `null`。 | 审计 #2 已修复 |
| 5 | 中 | 事务一致性 | `ContentManageServiceImpl.update` | 先删除旧文件再写新文件，数据库事务无法回滚文件系统。 | 审计 #2 已修复 |
| 6 | 中 | 业务语义 | `ContentManageServiceImpl.resolveBookSlug` | 小说目录使用第一个分类 slug，未校验书级分类。 | 审计 #2 已修复 |
| 7 | 中 | 参数校验 | `ContentQuery` | `page`、`size` 没有边界校验，`getOffset()` 可产生负偏移。 | 审计 #2 已修复 |
| 8 | 中 | 限流 | `RateLimitInterceptor` | IP 记录不清理；无条件信任 `X-Forwarded-For`。 | 审计 #2 已修复 |
| 9 | 中 | 数据库 | `schema.sql` | 无外键和级联约束，Service 漏删会产生孤儿记录。 | 审计 #2 已修复 |
| 10 | 中 | 前端能力 | 多个 Vue 页面 | 文档曾把页面写成已实现，实际除登录/布局/404 外均为占位。 | 文档已修正，功能待实现 |
| 11 | 中 | 接口契约 | `api-reference.md` 等 | 旧文档错误描述正常响应包装、201 状态码、全文搜索和子分类筛选。 | 文档已修正 |
| 12 | 低 | 配置 | `app.admin-path` | 配置项未使用，前端硬编码 `/secret-admin`。 | 审计 #2 已修复 |
| 13 | 低 | 上传校验 | `ImageServiceImpl` | 只检查客户端 MIME，不检查文件签名。 | 审计 #2 已修复 |
| 14 | 低 | 爬虫控制 | `index.html`/`public` | 有 `noindex`，没有 `robots.txt`。 | 待修复 |
| 15 | 低 | 测试 | 后端测试 | `UmoWebApplicationTests` 是空测试；没有真实 MySQL/文件系统集成测试。 | 部分修复，真实 MySQL 仍待补充 |

### 已确认的正确事实

- 公开端 8 个接口，管理端 19 个接口。
- 正常响应直接返回数据，不套 `{ code, data }`。
- 所有新建接口成功返回 200。
- 删除、修改密码、更新配置成功返回 204。
- 搜索只匹配 `title` 和 `summary`。
- `categoryId` 精确匹配，不自动包含子分类。
- 图片最大 50MB。
- 前端技术栈为 Vue 3 + Vite 8 + Vue Router 5 + Pinia 3。
- 公开详情当前分类和标签为 `null`，不能按列表响应结构推断。

### 验证记录

| 验证 | 结果 |
|---|---|
| `npm run build` | 通过，Vite 8.1.0 构建成功 |
| `mvn test` | 通过。使用隔离临时 Maven settings，21 个测试全部通过 |
| 真实 MySQL | 未执行 |
| 浏览器 E2E | 未执行 |

### 后续处理建议

1. 修正分类和标签删除保护的查询。
2. 为 Markdown 和 slug 路径增加 storage root 边界检查。
3. 统一文件与数据库失败处理策略。
4. 增加 `page`、`size` 校验。
5. 增加真实 Mapper 集成测试。
6. 完成前端页面后补浏览器回归。

## 审计 #2 - 2026-09-11

### 范围

- 前端公开路由守卫和管理路径配置。
- 内容 Markdown 文件与数据库一致性。
- 分类/标签删除保护、路径穿越、上传签名。
- JWT tokenVersion、登录限流、搜索限流可信代理。
- 分页/枚举/metadata 校验、公开详情组装、列表批量查询。
- SQL 索引、外键和旧库兼容迁移。

### 修复

| # | 级别 | 结果 |
|---|---|---|
| 1 | P0 | 公开路由仅在 `requiresAuth === true` 时要求 token；登录页、404 和无 meta 公开路由可访问。 |
| 2 | P0 | 创建拒绝覆盖已有 Markdown；更新使用临时文件、原子替换、备份恢复和提交后清理；删除采用数据库优先策略。 |
| 3 | P1 | 分类/标签按各自 ID 统计关联并返回 409；有子分类的父分类禁止删除；保存文章验证关联目标存在。 |
| 4 | P1 | slug/path 使用安全字符、normalize、storage root 边界检查；上传校验 MIME + 文件签名并使用固定扩展名。 |
| 5 | P1 | `dev` 明确允许默认凭据，`prod` 遇到默认 JWT secret/管理员密码拒绝启动；日志不输出密码或 secret。 |
| 6 | P1 | JWT 包含 tokenVersion；改密递增版本使旧 token 失效；登录失败按 username+IP 限流。 |
| 7 | P1 | 搜索限流使用原子窗口更新和过期清理，仅信任配置的代理，不再无条件信任 XFF。 |
| 8 | P2 | page/size、内容和分类/标签枚举、metadata JSON、长度和 slug 校验；非法输入返回 400。 |
| 9 | P2 | 公开详情与列表共享 `ContentVOMapper`，返回 categories/tags；列表按 contentIds 批量查询，消除 N+1。 |
| 10 | P2 | `schema.sql` 增加索引、外键和级联策略；新增兼容迁移清理孤儿并补列/索引/外键，无法添加时明确失败。 |
| 11 | 可维护性 | 管理分类/标签接口返回 VO；前端 `changePassword` API 已补；管理路径改为 `VITE_ADMIN_PATH`。 |

### 验证

| 验证 | 结果 |
|---|---|
| 后端完整 `mvn test` | 通过，64 tests / 0 failures / 0 errors |
| 前端 `npm run test:router` | 5 个路由守卫用例通过 |
| 前端 `npm run build` | Vite 8.1.0 生产构建通过 |
| `git diff --check` | 通过，仅有 Windows LF/CRLF 提示 |
| 敏感信息扫描 | 未发现真实 secret；命中的 `admin123`、JWT 默认值和 `sk-dummy-placeholder` 均为开发占位值 |
| 真实 MySQL 迁移 | 未执行，仓库无可用 MySQL 测试库 |

### 剩余风险

1. 未执行真实 MySQL 集成测试和迁移演练；生产执行前必须备份并先在副本验证。
2. 限流和登录失败计数仍是单实例内存状态，多实例部署需要 Redis 或网关共享限流。
3. 既有 Markdown 孤儿文件不会被迁移脚本自动扫描，文件删除失败只记录日志。

## 审计 #3 - 2026-09-11

### 复审发现与修复

| # | 级别 | 问题 | 结果 |
|---|---|---|---|
| 1 | P2 | `VITE_ADMIN_PATH` 只在路由根路径生效，登录跳转和多个管理链接仍硬编码 `/secret-admin`。 | 新增统一 `ADMIN_PATH` 工具，路由、登录页、布局和文章列表共用。 |
| 2 | P2 | 极大 `page` 与 `size` 相乘会整数溢出，导致负 offset 或 SQL 异常。 | page 限制为 1-1000000，offset 使用 long 计算并返回 400。 |
| 3 | P2 | metadata 为空白字符串时会写入 MySQL JSON 列并失败。 | 创建/更新统一将空白 metadata 规范化为 `null`。 |
| 4 | P2 | 分类更新可设置自身或祖先为父级，形成循环并导致树数据异常。 | 增加父级存在性和循环检查。 |
| 5 | P2 | 登录失败 key 区分大小写和首尾空格，可绕过失败次数限制。 | username 规范化后再作为限流 key 和查询条件。 |
| 6 | P3 | CORS 来源写死 localhost，生产部署无法通过配置切换。 | 增加 `app.cors.allowed-origins` / `CORS_ALLOWED_ORIGINS` 并测试。 |

### 验证

| 验证 | 结果 |
|---|---|
| 后端完整 `mvn test` | 通过，72 tests / 0 failures / 0 errors |
| 前端 `npm run test:router` | 7 个路由/管理路径用例通过 |
| 前端 `npm run build` | Vite 8.1.0 生产构建通过 |

### 剩余风险

1. 分类循环防护为 Service 检查，并发更新仍应结合数据库事务锁或更严格的层级模型。
2. 未执行真实浏览器 E2E 和真实 MySQL 迁移演练。
3. 限流状态仍为单实例内存实现。

## 审计 #4 - 2026-09-11 — 数据库迁移副本演练与全接口冒烟

### 环境

- 使用独立临时 MySQL 5.7 实例，端口 `3307`，未连接或修改系统 `3306` 上的真实库。
- 迁移前库由提交 `8369ac0` 的旧版 `schema.sql` 和当前种子数据建立。
- 额外注入 4 条孤儿关联和 1 条悬空父分类，用于验证兼容脚本的清理逻辑。
- 后端连接还原后的 `umo_blog_copy`，运行端口 `18080`，存储目录使用独立临时目录。

### 启动阻断与修复

| # | 级别 | 问题 | 修复 |
|---|---|---|---|
| 1 | P1 | Mapper XML 使用 `ContentCategoryLink` / `ContentTagLink` 简单别名，但 MyBatis 只扫描 entity 包，完整 Context 无法启动。 | 别名扫描扩展到 entity 和 dto，并新增 `MapperConfigurationTest`。 |
| 2 | P1 | `ClientIpResolver` 有两个构造器且生产构造器未标注注入，Spring 找不到默认构造器。 | 生产构造器显式增加 `@Autowired`，并新增容器装配测试。 |
| 3 | P1 | 业务代码注入 Jackson 2 `com.fasterxml` ObjectMapper，但 Spring Boot 4 自动配置提供的是 Jackson 3 `tools.jackson` ObjectMapper。 | 主代码和测试统一迁移到 Jackson 3，并新增自动配置容器测试。 |

### 迁移副本结果

| 验证 | 结果 |
|---|---|
| 逻辑备份 | 12,881 字节，SHA-256 `70C8E7E33DB2815EFF5BB17EE1E5FA620AB594BE4E81F2D013E023A96E4C7C9A` |
| 副本还原 | `umo_blog_copy` 包含 8 张表 |
| 首次迁移 | 成功新增 `users.token_version`、3 个索引、5 个外键 |
| 第二次迁移 | 成功，无重复列/索引/外键，验证幂等 |
| 数据清理 | 孤儿分类关联 2→0，孤儿标签关联 2→0，悬空父分类 1→0 |
| 数据保留 | users/categories/tags/contents/content_category/content_tag/images/site_options = `0/11/9/6/9/12/0/4` |
| 源库隔离 | 源库仍无 `token_version`，迁移只作用于副本 |
| 外键动作 | 2 个内容外键为 CASCADE，3 个分类/标签外键为 RESTRICT |

### 全接口冒烟

复用脚本 `Server Side/UmoWebBackend/scripts/api-smoke.ps1`，公开端 8 个和管理端 19 个接口全部通过，
结果为 `27/27`。

额外验证：

- 管理端无有效 JWT 返回 401。
- 修改密码返回 204，旧 token 随即返回 401。
- 搜索首次返回 200，10 秒内重复请求返回 429。
- 1×1 PNG 通过 MIME + 文件签名校验并返回可访问 URL。
- 测试分类、标签和草稿文章均已清理，`site_title` 和管理员密码已恢复。

### 验证记录

| 验证 | 结果 |
|---|---|
| 后端完整 `mvn test` | 通过，75 tests / 0 failures / 0 errors |
| 真实 MySQL 迁移副本 | 通过，连续执行两次 |
| 全接口冒烟 | 通过，27/27 |
| `git diff --check` | 通过，仅有 Windows LF/CRLF 提示 |

### 剩余风险

1. 真实 MySQL 验证仍是手工编排的冒烟，不是 CI 自动集成测试。
2. 测试图片上传后没有删除接口，因此会在冒烟使用的测试存储中留下 1 条图片记录和文件。
3. 搜索/登录限流仍是单实例内存状态。
