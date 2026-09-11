# UmoWeb 前端续作提示

> 基线日期: 2026-09-11
> 目标: 在不误改现有基础设施的前提下完成剩余前端业务页面
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
| `src/api/admin.js` | 19 个管理 API 函数，包含 `changePassword` |
| `src/stores/auth.js` | token、login、logout |
| `src/stores/site.js` | siteTitle、siteSubtitle、状态和缓存加载 |
| `src/utils/adminContent.js` | 管理端查询、表单、metadata、分类顺序和图片插入规则 |
| `src/utils/adminManagement.js` | 分类父级、分类/标签/站点/改密校验和删除错误映射 |
| `src/views/admin/LoginPage.vue` | 已实现 |
| `src/components/admin/AdminLayout.vue` | 已实现响应式布局 |
| `src/views/admin/ContentListPage.vue` | 已接入列表、筛选、分页和删除 |
| `src/views/admin/ContentEditPage.vue` | 已接入新建/编辑、预览和图片上传 |
| `src/views/admin/CategoryManagePage.vue` | 已接入树形 CRUD |
| `src/views/admin/TagManagePage.vue` | 已接入列表 CRUD |
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
| `PostDetailPage.vue` | 已接入详情、分类标签和前后文章 |
| `AboutPage.vue` | 已接入 About 页面 API |
| `ProjectPage.vue` | 已接入 Project 页面 API |
| `EditorPage.vue` | 待实现本地导入、编辑、预览、下载 |

### 4.2 管理端

| 页面 | 当前状态 |
|---|---|
| `ContentListPage.vue` | 已接入文章表格、筛选、删除、分页 |
| `ContentEditPage.vue` | 已接入新建/编辑、分类标签、Markdown 预览和图片上传 |
| `CategoryManagePage.vue` | 已接入树形 CRUD、父级防循环和 409 提示 |
| `TagManagePage.vue` | 已接入列表 CRUD 和 409 提示 |
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

1. 完成本地编辑器。
2. 增加管理端和公开端的可重复浏览器 E2E。
3. 做剩余视觉回归。

---

## 7. 不要假设的能力

以下能力当前不存在：

- 统一正常响应包装。
- 新建接口返回 201。
- 公开在线编辑器的导入/下载流程。
- 内容、分类、标签通用 store。
- 可重复执行的浏览器 E2E 工程。
- 自动包含子分类的筛选。
- 全文搜索 Markdown 正文。

这些缺口必须按 [codebase-memory.md](codebase-memory.md) 处理。
