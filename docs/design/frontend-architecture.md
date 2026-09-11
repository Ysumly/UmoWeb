# UmoWeb 前端架构

> 基线日期: 2026-09-11
> 项目路径: `Client Side/umo-web-frontend/`
> 状态: 公开端主路径和管理端文章工作流已接入真实 API，其余管理业务页仍待继续实现

---

## 1. 技术栈

| 技术 | 实际版本 | 用途 |
|---|---|---|
| Vue | 3.5.x | Composition API |
| Vite | 8.1.x | 开发服务器和构建 |
| Vue Router | 5.1.x | SPA 路由 |
| Pinia | 3.0.x | 状态管理 |
| Axios | 1.18.x | REST 请求 |
| marked | 18.x | 公开详情、About 和 Project 的 Markdown 渲染能力 |
| highlight.js | 11.x | Markdown 代码块高亮，按语言注册 |
| Tailwind CSS | 4.3.x | utility-first 样式 |

当前没有 TypeScript、SSR、Nuxt、CodeMirror 或 Monaco。

---

## 2. 实际目录

```text
umo-web-frontend/
├── index.html
├── package.json
├── package-lock.json
├── vite.config.js
├── public/
│   ├── favicon.svg
│   └── icons.svg
└── src/
    ├── main.js
    ├── App.vue
    ├── style.css
    ├── directives/
    │   └── reveal.js
    ├── config/
    │   └── contentTypes.js
    ├── layouts/
    │   └── PublicLayout.vue
    ├── theme/
    │   ├── theme.js
    │   └── useTheme.js
    ├── utils/
    │   ├── adminContent.js
    │   ├── apiError.js
    │   ├── format.js
    │   ├── markdown.js
    │   └── publicContent.js
    ├── api/
    │   ├── client.js
    │   ├── public.js
    │   └── admin.js
    ├── router/
    │   └── index.js
    ├── stores/
    │   ├── auth.js
    │   └── site.js
    ├── components/
    │   ├── common/
    │   │   └── ThemeToggle.vue
    │   ├── public/
    │   │   ├── ContentCard.vue
    │   │   ├── ContentState.vue
    │   │   ├── MarkdownArticle.vue
    │   │   ├── SectionHeading.vue
    │   │   ├── SiteFooter.vue
    │   │   └── SiteHeader.vue
    │   └── admin/
    │       └── AdminLayout.vue
    └── views/
        ├── public/
        │   ├── HomePage.vue
        │   ├── LibraryPage.vue
        │   ├── SearchPage.vue
        │   ├── PostDetailPage.vue
        │   ├── AboutPage.vue
        │   ├── ProjectPage.vue
        │   ├── EditorPage.vue
        │   └── NotFoundPage.vue
        └── admin/
            ├── LoginPage.vue
            ├── ContentListPage.vue
            ├── ContentEditPage.vue
            ├── CategoryManagePage.vue
            ├── TagManagePage.vue
            └── OptionPage.vue
```

公开端已有主题、布局、Markdown 和内容卡片组件。当前没有 TypeScript、SSR、组件库或端到端测试工程。

---

## 3. 应用入口

`main.js` 只负责：

1. 创建 Vue app。
2. 安装 Pinia。
3. 安装 Router。
4. 注册 `v-reveal` 滚动揭示指令。
5. 引入全局 Tailwind 与主题样式。
6. 挂载 `#app`。

`App.vue` 初始化主题状态并渲染根级 `<router-view />`；公开路由由 `PublicLayout.vue` 提供 Header、Footer 和页面转场。

---

## 4. 路由

实际路由：

| 路径 | 名称 | 组件 | 状态 |
|---|---|---|---|
| `/` | `home` | `HomePage.vue` | 已接入真实 API |
| `/library` | `library` | `LibraryPage.vue` | 已接入真实 API |
| `/search` | `search` | `SearchPage.vue` | 已接入真实 API |
| `/post/:slug` | `post` | `PostDetailPage.vue` | 已接入真实 API |
| `/about` | `about` | `AboutPage.vue` | 已接入真实 API |
| `/project` | `project` | `ProjectPage.vue` | 已接入真实 API |
| `/editor` | `editor` | `EditorPage.vue` | 占位 |
| `/secret-admin` | - | `AdminLayout.vue` | 重定向到文章页 |
| `/secret-admin/login` | `login` | `LoginPage.vue` | 已实现 |
| `/secret-admin/contents` | `admin-contents` | `ContentListPage.vue` | 已接入真实 API |
| `/secret-admin/contents/new` | `content-new` | `ContentEditPage.vue` | 已接入真实 API |
| `/secret-admin/contents/:id/edit` | `content-edit` | `ContentEditPage.vue` | 已接入真实 API |
| `/secret-admin/categories` | `admin-cats` | `CategoryManagePage.vue` | 占位 |
| `/secret-admin/tags` | `admin-tags` | `TagManagePage.vue` | 占位 |
| `/secret-admin/options` | `admin-options` | `OptionPage.vue` | 占位 |
| `/:pathMatch(.*)*` | `not-found` | `NotFoundPage.vue` | 已实现 |

守卫逻辑直接读取 `localStorage.token`，没有在路由进入时调用后端验证 token。

公开路由通过 `meta.motion` 区分 `cinematic` 和 `focused` 动效等级。

---

## 5. API 层

### 5.1 Axios 实例

`src/api/client.js`：

- `baseURL: '/api'`。
- 超时 15 秒。
- 请求前从 `localStorage` 读取 token，并设置 Bearer Header。
- 响应为 401 时删除 token，并跳转 `login`。

因为 `baseURL` 是相对路径，开发时依赖 `vite.config.js` 将 `/api` 代理到 `http://localhost:8080`。

### 5.2 API 封装数量

| 文件 | 实际函数数 | 内容 |
|---|---:|---|
| `api/public.js` | 8 | 8 个公开端接口 |
| `api/admin.js` | 19 | 19 个管理端函数，包含修改密码 |

---

## 6. Pinia

### 6.1 auth store

已实现：

- `token`
- `login(username, password)`
- `logout()`

token 来源和存储位置都是 `localStorage`。

### 6.2 site store

当前字段：

- `title`
- `subtitle`
- `fetch()`

`fetch()` 在读取 `res.data` 时同时兼容 `res.data.siteTitle` 和 `res.data.data?.siteTitle`，但后端实际直接返回前者。

当前不存在 `content`、`category` 或通用请求状态 store。

---

## 7. 页面实现状态

### 7.1 已实现

| 页面/组件 | 实际能力 |
|---|---|
| `LoginPage.vue` | 表单、调用登录 API、错误提示、跳转 |
| `AdminLayout.vue` | 桌面侧栏、移动抽屉、主题切换、退出登录和 `router-view` |
| `ContentListPage.vue` | 文章筛选、分页、状态展示、编辑和删除 |
| `ContentEditPage.vue` | 新建/编辑、分类标签、metadata、Markdown 分屏预览、图片上传和未保存保护 |
| `NotFoundPage.vue` | 公开端视觉样式，提供返回首页和书库入口 |

### 7.2 公开端真实 API

以下页面读取现有公开接口，并提供加载、空数据、错误和重试状态：

- 首页：站点介绍、主推文章、最新内容、类型入口和 About 预览。
- 书库：类型、分类、标签服务端筛选和分页。
- 搜索：显式提交、URL 同步和 429 倒计时。
- 文章详情：Markdown、代码高亮、分类标签和后端返回的前后文章。
- About 与 Project：分别读取配置页 Markdown。

主题由 `data-theme` 控制，亮暗偏好保存在 `localStorage`。公开端支持首页电影化动效、滚动揭示和减少动态偏好。

### 7.3 占位

公开端：

- Markdown 编辑器

管理端：

- 分类管理
- 标签管理
- 站点设置
- 修改密码入口

公开在线编辑器仍只渲染占位内容；管理端文章工作流已实现，其余页面仍为占位。

---

## 8. 开发与构建

```powershell
cd "Client Side\umo-web-frontend"
npm install
npm run dev
npm run build
```

Vite 开发配置：

```js
server: {
  port: 5173,
  proxy: {
    '/api': 'http://localhost:8080',
    '/images': 'http://localhost:8080'
  }
}
```

2026-09-11 执行 `npm test` 和 `npm run build` 成功。当前 29 个 Node 测试覆盖路由、管理路径、主题解析、管理端文章表单规则、静态筛选和 Markdown 原始 HTML 禁用。

---

## 9. 下一步实现顺序

1. 实现分类、标签和站点配置管理。
2. 补齐修改密码入口。
3. 实现在线 Markdown 编辑器。
4. 增加可重复执行的浏览器 E2E 和视觉回归。
