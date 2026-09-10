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
| 1 | 高 | 数据完整性 | `CategoryManageServiceImpl.delete` | 把分类 ID 当作内容 ID 调用 `findCategoryIdsByContentId`，分类删除保护不能可靠工作。 | 待修复 |
| 2 | 高 | 数据完整性 | `TagManageServiceImpl.delete` | 把标签 ID 当作内容 ID 调用 `findTagIdsByContentId`，标签删除保护不能可靠工作。 | 待修复 |
| 3 | 高 | 文件安全 | `FileUtil` + 内容保存 | `slug`、`bookSlug` 未检查路径穿越，最终路径可能越出 storage root。 | 待修复 |
| 4 | 中 | 响应语义 | `ContentServiceImpl.getBySlug` | 公开详情未组装分类和标签，接口实际返回 `null`。 | 待修复 |
| 5 | 中 | 事务一致性 | `ContentManageServiceImpl.update` | 先删除旧文件再写新文件，数据库事务无法回滚文件系统。 | 待修复 |
| 6 | 中 | 业务语义 | `ContentManageServiceImpl.resolveBookSlug` | 小说目录使用第一个分类 slug，未校验书级分类。 | 待修复 |
| 7 | 中 | 参数校验 | `ContentQuery` | `page`、`size` 没有边界校验，`getOffset()` 可产生负偏移。 | 待修复 |
| 8 | 中 | 限流 | `RateLimitInterceptor` | IP 记录不清理；无条件信任 `X-Forwarded-For`。 | 待修复 |
| 9 | 中 | 数据库 | `schema.sql` | 无外键和级联约束，Service 漏删会产生孤儿记录。 | 待修复 |
| 10 | 中 | 前端能力 | 多个 Vue 页面 | 文档曾把页面写成已实现，实际除登录/布局/404 外均为占位。 | 文档已修正，功能待实现 |
| 11 | 中 | 接口契约 | `api-reference.md` 等 | 旧文档错误描述正常响应包装、201 状态码、全文搜索和子分类筛选。 | 文档已修正 |
| 12 | 低 | 配置 | `app.admin-path` | 配置项未使用，前端硬编码 `/secret-admin`。 | 待决策 |
| 13 | 低 | 上传校验 | `ImageServiceImpl` | 只检查客户端 MIME，不检查文件签名。 | 待修复 |
| 14 | 低 | 爬虫控制 | `index.html`/`public` | 有 `noindex`，没有 `robots.txt`。 | 待修复 |
| 15 | 低 | 测试 | 后端测试 | `UmoWebApplicationTests` 是空测试；没有真实 MySQL/文件系统集成测试。 | 待补充 |

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
