# UmoWeb 前端架构

> 基线日期: 2026-09-11
> 项目路径: `Client Side/umo-web-frontend/`
> 状态: 公开端主路径已实现静态视觉 MVP，搜索、Project、编辑器和管理端仍待继续实现

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
    ├── demo/
    │   └── content.js
    ├── layouts/
    │   └── PublicLayout.vue
    ├── theme/
    │   ├── theme.js
    │   └── useTheme.js
    ├── utils/
    │   └── markdown.js
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
| `/` | `home` | `HomePage.vue` | 静态视觉 MVP |
| `/library` | `library` | `LibraryPage.vue` | 静态视觉 MVP |
| `/search` | `search` | `SearchPage.vue` | 占位 |
| `/post/:slug` | `post` | `PostDetailPage.vue` | 静态视觉 MVP |
| `/about` | `about` | `AboutPage.vue` | 静态视觉 MVP |
| `/project` | `project` | `ProjectPage.vue` | 占位 |
| `/editor` | `editor` | `EditorPage.vue` | 占位 |
| `/secret-admin` | - | `AdminLayout.vue` | 重定向到文章页 |
| `/secret-admin/login` | `login` | `LoginPage.vue` | 已实现 |
| `/secret-admin/contents` | `admin-contents` | `ContentListPage.vue` | 占位 |
| `/secret-admin/contents/new` | `content-new` | `ContentEditPage.vue` | 占位 |
| `/secret-admin/contents/:id/edit` | `content-edit` | `ContentEditPage.vue` | 占位 |
| `/secret-admin/categories` | `admin-cats` | `CategoryManagePage.vue` | 占位 |
| `/secret-admin/tags` | `admin-tags` | `TagManagePage.vue` | 占位 |
| `/secret-admin/options` | `admin-options` | `OptionPage.vue` | 占位 |
| `/:pathMatch(.*)*` | `not-found` | `NotFoundPage.vue` | 静态视觉 MVP |

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
| `api/admin.js` | 18 | 18 个管理端函数 |

`admin.js` 当前缺少修改密码函数：

```js
changePassword(data) => client.put('/admin/change-password', data)
```

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
| `AdminLayout.vue` | 侧栏导航、退出登录、`router-view` |
| `NotFoundPage.vue` | 静态视觉 MVP，提供返回首页和书库入口 |

### 7.2 静态视觉 MVP

以下页面使用 `src/demo/content.js` 本地数据，不调用真实 API：

- 首页：站点介绍、主推文章、最新内容、类型入口和 About 预览。
- 书库：类型、分类、标签本地筛选和分页。
- 文章详情：Markdown、代码高亮、分类标签和前后文章。
- About：静态 Markdown 阅读页。

主题由 `data-theme` 控制，亮暗偏好保存在 `localStorage`。公开端支持首页电影化动效、滚动揭示和减少动态偏好。

### 7.3 占位

公开端：

- 搜索
- Project
- Markdown 编辑器

管理端：

- 文章列表
- 文章编辑
- 分类管理
- 标签管理
- 站点设置

搜索、Project 和编辑器仍只渲染标题与“待实现”文本；管理端仍为原有基础设施和占位页面。

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

2026-09-11 执行 `npm test` 和 `npm run build` 成功。Node 测试覆盖路由、管理路径、主题解析、静态筛选和 Markdown 原始 HTML 禁用。

---

## 9. 下一步实现顺序

1. 将公开端静态 fixtures 替换为现有 8 个公开 API，并补齐加载、空数据和错误状态。
2. 实现真实搜索、Project、在线编辑器和图片上传交互。
3. 实现文章管理列表和编辑表单。
4. 实现分类、标签和站点配置管理。
5. 补齐修改密码入口和 API。
6. 增加浏览器 E2E 和视觉回归。
