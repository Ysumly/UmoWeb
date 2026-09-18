# UmoWeb 前端续作提示

> 基线日期: 2026-09-15
> 目标: 在不误改现有基础设施的前提下维护已完成的公开端、在线编辑器和管理端业务页面
> 事实来源: `Client Side/umo-web-frontend/src`

---

## 1. 当前技术栈

| 技术 | 版本 |
|---|---|
| Vue | 3.5.x |
| Vite | 8.x |
| Vue Router | 5.x |
| Pinia | 3.x |
| Axios | 1.18.x |
| Tailwind CSS | 4.x |
| marked | 18.x |
| highlight.js | 11.x |
| yaml | 2.9.x |

不要引入 CodeMirror、Monaco 或新的状态/API 框架，除非需求明确改变。

---

## 2. 后端事实

- 公开端 8 个接口。
- 管理端 32 个接口，包含批量文章操作、图片列表、图片一致性检查、图片删除、修改密码、
  AI 模式目录和转换运行时；AI 设置页已实现，文章 AI 抽屉仍待后续实现。
- 管理端除登录外都需要 Bearer JWT。
- 正常响应不套 `{ code, data }`。
- 错误响应是 `{ code, message }`。
- 新建资源成功状态是 200，不是 201。
- 删除、修改密码、更新配置返回 204。
- 图片最大 50MB。

完整字段和响应见 [api-reference.md](../design/api-reference.md)。

---

## 3. 已实现文件

| 文件 | 状态 |
|---|---|
| `src/main.js` | Pinia + Router 已挂载 |
| `src/App.vue` | 仅 `<router-view />` |
| `src/router/index.js` | 路由和 Token 守卫已实现 |
| `src/api/client.js` | Axios、JWT、401 处理已实现 |
| `src/api/public.js` | 8 个公开 API 函数 |
| `src/api/admin.js` | 28 个管理 API 函数，包含 `changePassword` 和六个 AI 模式接口 |
| `src/stores/auth.js` | token、login、logout |
| `src/stores/site.js` | siteTitle、siteSubtitle、状态和缓存加载 |
| `src/utils/adminContent.js` | 管理端查询、表单、metadata、分类顺序和图片插入规则 |
| `src/utils/markdownImport.js` | Markdown 文件校验、YAML front matter 解析、字段回退和图片引用警告 |
| `src/utils/adminManagement.js` | 分类父级、分类/标签/站点/改密校验和删除错误映射 |
| `src/utils/aiModeSettings.js` | AI 模式表单、校验、创建/复制/更新载荷、排序和版本格式化 |
| `src/utils/articleOutline.js` | 目录路径、扁平化和展开行计算 |
| `src/utils/editor.js` | 本地编辑器文件名、草稿序列化和 Markdown Blob |
| `src/views/admin/LoginPage.vue` | 已实现 |
| `src/components/admin/AdminLayout.vue` | 已实现响应式布局 |
| `src/views/admin/ContentListPage.vue` | 已接入列表、筛选、分页和删除 |
| `src/views/admin/ContentEditPage.vue` | 已接入新建/编辑、Markdown 导入、预览和图片上传 |
| `src/views/admin/CategoryManagePage.vue` | 已接入树形 CRUD |
| `src/views/admin/TagManagePage.vue` | 已接入列表 CRUD |
| `src/views/admin/AiSettingsPage.vue` | 已接入模式 CRUD、启停、排序、版本预览和回滚 |
| `src/views/admin/OptionPage.vue` | 已接入站点配置读写和 Markdown 预览 |
| `src/views/admin/ChangePasswordPage.vue` | 已接入密码修改和会话清理 |
| `src/views/public/NotFoundPage.vue` | 已实现基础 404 |

---

## 4. 页面状态

### 4.1 公开端

| 页面 | 状态 |
|---|---|
| `HomePage.vue` | 已接入站点信息、列表与类型统计 API |
| `LibraryPage.vue` | 已接入服务端筛选和分页 |
| `SearchPage.vue` | 已接入显式搜索、URL 同步和 429 倒计时 |
| `PostDetailPage.vue` | 已接入详情、分类标签、前后文章、目录、章节高亮、阅读进度和相关阅读 |
| `AboutPage.vue` | 已接入 About 页面 API |
| `ProjectPage.vue` | 已接入 Project 页面 API |
| `EditorPage.vue` | 已实现本地导入、编辑、预览、下载和草稿恢复 |

### 4.2 管理端

| 页面 | 当前状态 |
|---|---|
| `ContentListPage.vue` | 已接入文章表格、筛选、删除、分页 |
| `ContentEditPage.vue` | 已接入新建/编辑、Markdown front matter 导入、分类标签、预览和图片上传 |
| `CategoryManagePage.vue` | 已接入树形 CRUD、父级防循环和 409 提示 |
| `TagManagePage.vue` | 已接入列表 CRUD 和 409 提示 |
| `AiSettingsPage.vue` | 已接入模式列表、创建、复制、编辑、启停、排序、版本预览和回滚 |
| `OptionPage.vue` | 已接入站点标题、副标题、About/Project 编辑与保存 |
| `ChangePasswordPage.vue` | 已接入密码修改、旧 token 清理和重新登录提示 |

---

## 5. 实现要求

### 5.1 API 使用

- 使用现有 Axios 实例。
- 不要把响应统一当成 `response.data.data`。
- 列表使用 `response.data.items`。
- 错误消息优先读取 `error.response.data.message`。
- 业务接口 401 由拦截器清理会话；登录页和“旧密码错误”在当前页面展示。

### 5.2 Markdown

- 使用 `marked` 渲染。
- 使用 `highlight.js` 做代码高亮。
- 禁用或净化不受信任的原始 HTML。
- 图片 URL 可能是 `/images/...`，开发环境由 Vite 代理。
- 公开在线编辑器只使用 `umo-editor-draft-v1` 保存本地草稿，不上传后端。

### 5.3 文章表单

- `metadata` 输入必须提交 JSON 字符串。
- 文章类型为 `NOTE`、`NOVEL`、`BOOK_REVIEW`。
- 状态为 `DRAFT`、`PUBLISHED`。
- 小说 `bookSlug` 当前取 `categoryIds[0]` 的 slug，前端应明确选择书级分类。
- 修改 slug 会移动 Markdown 文件，应提示唯一性和影响。
- 新建页可导入单个 `.md`/`.markdown` 文件；YAML front matter 缺失时从 H1、文件名和默认值回退。
- 导入只预填表单，保存仍走现有创建接口；不覆盖已有文章，不批量导入，不自动上传相对图片。

### 5.4 加载与错误态

每个数据页面至少提供：

- 加载态。
- 空数据态。
- 请求错误态。
- 401、404、409、413、429 的用户可理解提示。

### 5.5 响应式

- 管理侧栏移动端需要折叠方案。
- Markdown 编辑和预览在小屏切换。
- 表格在移动端应转为卡片或允许横向滚动。

---

## 6. 推荐实施顺序

后续实施顺序已调整为 Post-v1 路线：

1. 正式上线与数据安全。
2. 工程交付、质量基线与安全统计：CI、Linux 视觉基线、真实 MySQL 集成和最小化访问日志
   已在 `v1.0.0-rc.5` 通过 ECS 发布和核心路由验收。
3. 功能设计；Task 3.1 Markdown 导入、Task 3.2 子分类筛选、Task 3.3 图片删除、
   Task 3.4 正文全文搜索和 Task 3.5 四款训练游戏已完成并合并 master，第三阶段出口条件满足。
4. 第四阶段 Task 4.1–4.3 已完成并在 `v1.0.0-rc.6` 发布：目录、阅读进度、相关阅读、
   图片一致性、批量管理、归档和定时发布均已交付，不包含文章修订历史。
5. 第五阶段在需求、隐私和成本明确后评估管理端 AI；草稿和结果只保存在浏览器本地。
6. 公开 AI 搜索、原文问答和知识图谱继续后置。

完整顺序和出口条件见
[`../superpowers/plans/2026-09-14-post-v1-roadmap.md`](../superpowers/plans/2026-09-14-post-v1-roadmap.md)。
第一至第三阶段历史记录见
[`../superpowers/plans/2026-09-12-four-phase-roadmap.md`](../superpowers/plans/2026-09-12-four-phase-roadmap.md)。

---

## 7. 不要假设的能力

以下能力当前不存在：

- 统一正常响应包装。
- 新建接口返回 201。
- 内容、分类、标签通用 store。

这些缺口必须按 [codebase-memory.md](codebase-memory.md) 处理。
