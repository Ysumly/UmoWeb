# UmoWeb 前端架构

> 基线日期: 2026-09-11
> 项目路径: `Client Side/umo-web-frontend/`
> 状态: 公开端、公开在线编辑器和管理端核心业务页均已实现

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
| Playwright Test | 1.63.x | 本机 Chrome 浏览器 E2E 与视觉回归 |

当前没有 TypeScript、SSR、Nuxt、CodeMirror 或 Monaco。

---

## 2. 实际目录

```text
umo-web-frontend/
├── index.html
├── package.json
├── package-lock.json
├── playwright.config.js
├── vite.config.js
├── e2e/
│   ├── support/
│   │   └── apiMock.js
│   ├── admin.spec.js
│   ├── editor.spec.js
│   ├── public.spec.js
│   └── visual.spec.js
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
    │   ├── editor.js
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

公开端已有主题、布局、Markdown 和内容卡片组件。Playwright functional 与视觉项目复用本机 Chrome，并使用浏览器级 Mock API；当前没有 TypeScript、SSR 或组件库。

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
| `/editor` | `editor` | `EditorPage.vue` | 纯浏览器本地编辑器 |
| `/secret-admin` | - | `AdminLayout.vue` | 重定向到文章页 |
| `/secret-admin/login` | `login` | `LoginPage.vue` | 已实现 |
| `/secret-admin/contents` | `admin-contents` | `ContentListPage.vue` | 已接入真实 API |
| `/secret-admin/contents/new` | `content-new` | `ContentEditPage.vue` | 已接入真实 API |
| `/secret-admin/contents/:id/edit` | `content-edit` | `ContentEditPage.vue` | 已接入真实 API |
| `/secret-admin/categories` | `admin-cats` | `CategoryManagePage.vue` | 已接入真实 API |
| `/secret-admin/tags` | `admin-tags` | `TagManagePage.vue` | 已接入真实 API |
| `/secret-admin/options` | `admin-options` | `OptionPage.vue` | 已接入真实 API |
| `/secret-admin/password` | `admin-password` | `ChangePasswordPage.vue` | 已接入真实 API |
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
- 业务请求响应 401 时删除 token，并跳转 `login`。
- 登录和“旧密码错误”由对应页面展示，不会被拦截器提前清理当前会话；改密接口的其他 401 仍会清理 token。

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

- `siteTitle`
- `siteSubtitle`
- `status`
- `error`
- `load(force = false)`

`load()` 缓存已加载的站点标题和副标题，并支持强制刷新。

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
| `CategoryManagePage.vue` | 分类树筛选、父级/排序字段、增改删、409 提示和未保存保护 |
| `TagManagePage.vue` | 标签增改删、字段校验、409 提示和未保存保护 |
| `OptionPage.vue` | 站点信息、About/Project Markdown 预览、统一保存和部分失败反馈 |
| `ChangePasswordPage.vue` | 密码校验、修改后清 token、跳转登录页 |
| `EditorPage.vue` | `.md` 导入/下载、Markdown 编辑与安全预览、移动端切换和本地草稿恢复 |
| `NotFoundPage.vue` | 公开端视觉样式，提供返回首页和书库入口 |

### 7.2 公开端真实 API

以下页面读取现有公开接口，并提供加载、空数据、错误和重试状态：

- 首页：站点介绍、主推文章、最新内容、类型入口和 About 预览。
- 书库：类型、分类、标签服务端筛选和分页。
- 搜索：显式提交、URL 同步和 429 倒计时。
- 文章详情：Markdown、代码高亮、分类标签和后端返回的前后文章。
- About 与 Project：分别读取配置页 Markdown。

主题由 `data-theme` 控制，亮暗偏好保存在 `localStorage`。公开端支持首页电影化动效、滚动揭示和减少动态偏好。

### 7.3 公开在线编辑器

`EditorPage.vue` 不调用后端，使用原生 `textarea` 和 `MarkdownArticle` 完成分屏编辑/预览；窄屏切换单栏。支持导入与下载 `.md`、文件名规范化、导入替换确认和清空；草稿写入 `localStorage["umo-editor-draft-v1"]`，页面重新进入时恢复。

---

## 8. 开发与构建

```powershell
cd "Client Side\umo-web-frontend"
npm install
npm run dev
npm run build
npm run test:e2e
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

2026-09-11 执行 `npm test`、`npm run build` 和 `npm run test:e2e` 成功。当前 46 个 Node 测试覆盖路由、管理路径、主题解析、管理端文章/分类/标签/站点/改密规则、编辑器草稿与文件规则、静态筛选、Markdown 原始 HTML、危险 URL 协议和图片 alt 转义；Playwright 另含 16 个 functional 和 14 个视觉检查。

---

## 9. 下一步实现顺序

1. 将本地 Playwright 套件接入 CI，并为 Linux 环境建立独立视觉基线。
