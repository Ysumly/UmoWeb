# 审计日志

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
