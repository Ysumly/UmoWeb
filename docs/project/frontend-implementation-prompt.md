# UmoWeb 前端续作提示

> 基线日期: 2026-09-10
> 目标: 在不误改现有基础设施的前提下完成前端业务页面
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

不要引入 CodeMirror、Monaco 或新的状态/API 框架，除非需求明确改变。

---

## 2. 后端事实

- 公开端 8 个接口。
- 管理端 19 个接口，包含修改密码。
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
| `src/api/admin.js` | 18 个管理 API 函数，缺 `changePassword` |
| `src/stores/auth.js` | token、login、logout |
| `src/stores/site.js` | title、subtitle、fetch |
| `src/views/admin/LoginPage.vue` | 已实现 |
| `src/components/admin/AdminLayout.vue` | 已实现基础布局 |
| `src/views/public/NotFoundPage.vue` | 已实现基础 404 |

---

## 4. 待实现页面

### 4.1 公开端

| 页面 | 任务 |
|---|---|
| `HomePage.vue` | 站点信息、最新文章、分类入口 |
| `LibraryPage.vue` | 文章列表、分类/标签/type 筛选、分页 |
| `SearchPage.vue` | URL 查询同步、搜索、429 提示 |
| `PostDetailPage.vue` | 详情、Markdown 渲染、代码高亮、404 |
| `AboutPage.vue` | 获取 Markdown 并渲染 |
| `ProjectPage.vue` | 获取 Markdown 并渲染 |
| `EditorPage.vue` | 本地导入、编辑、预览、下载 |

### 4.2 管理端

| 页面 | 任务 |
|---|---|
| `ContentListPage.vue` | 文章表格、筛选、删除、分页 |
| `ContentEditPage.vue` | 新建/编辑表单、分类标签选择、Markdown 预览 |
| `CategoryManagePage.vue` | 树形 CRUD |
| `TagManagePage.vue` | 列表 CRUD |
| `OptionPage.vue` | 站点标题、副标题、About/Project 编辑 |

还需要增加修改密码 API 函数、页面或入口。

---

## 5. 实现要求

### 5.1 API 使用

- 使用现有 Axios 实例。
- 不要把响应统一当成 `response.data.data`。
- 列表使用 `response.data.items`。
- 错误消息优先读取 `error.response.data.message`。
- 401 处理已由拦截器负责，不要在每页重复全局跳转。

### 5.2 Markdown

- 使用 `marked` 渲染。
- 使用 `highlight.js` 做代码高亮。
- 禁用或净化不受信任的原始 HTML。
- 图片 URL 可能是 `/images/...`，开发环境由 Vite 代理。

### 5.3 文章表单

- `metadata` 输入必须提交 JSON 字符串。
- 文章类型为 `NOTE`、`NOVEL`、`BOOK_REVIEW`。
- 状态为 `DRAFT`、`PUBLISHED`。
- 小说 `bookSlug` 当前取 `categoryIds[0]` 的 slug，前端应明确选择书级分类。
- 修改 slug 会移动 Markdown 文件，应提示唯一性和影响。

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

1. 补齐 `changePassword` 和 Markdown 渲染工具。
2. 完成文章详情、首页、分类列表、搜索。
3. 完成文章管理列表和编辑表单。
4. 完成分类、标签、站点设置。
5. 完成本地编辑器。
6. 做响应式和错误态回归。

---

## 7. 不要假设的能力

以下能力当前不存在：

- 统一正常响应包装。
- 新建接口返回 201。
- 前端 Markdown 渲染组件。
- 图片拖入/粘贴组件。
- 修改密码页面。
- 内容、分类、标签通用 store。
- 前端自动化测试。
- 自动包含子分类的筛选。
- 全文搜索 Markdown 正文。

这些缺口必须按 [codebase-memory.md](codebase-memory.md) 处理。
