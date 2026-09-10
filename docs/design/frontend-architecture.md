# UmoWeb 前端架构

> 基线日期: 2026-09-10
> 项目路径: `Client Side/umo-web-frontend/`
> 状态: 基础设施已实现，大部分业务页面仍为占位页

---

## 1. 技术栈

| 技术 | 实际版本 | 用途 |
|---|---|---|
| Vue | 3.5.x | Composition API |
| Vite | 8.1.x | 开发服务器和构建 |
| Vue Router | 5.1.x | SPA 路由 |
| Pinia | 3.0.x | 状态管理 |
| Axios | 1.18.x | REST 请求 |
| marked | 18.x | 已安装，尚未在页面使用 |
| highlight.js | 11.x | 已安装，尚未在页面使用 |
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

当前不存在 `composables/`、`utils/`、公开端公共组件或通用 loading/error/pagination 组件。

---

## 3. 应用入口

`main.js` 只负责：

1. 创建 Vue app。
2. 安装 Pinia。
3. 安装 Router。
4. 引入全局 Tailwind 样式。
5. 挂载 `#app`。

`App.vue` 直接渲染 `<router-view />`，没有全局 Header 或 Footer。

---

## 4. 路由

实际路由：

| 路径 | 名称 | 组件 | 状态 |
|---|---|---|---|
| `/` | `home` | `HomePage.vue` | 占位 |
| `/library` | `library` | `LibraryPage.vue` | 占位 |
| `/search` | `search` | `SearchPage.vue` | 占位 |
| `/post/:slug` | `post` | `PostDetailPage.vue` | 占位 |
| `/about` | `about` | `AboutPage.vue` | 占位 |
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
| `/:pathMatch(.*)*` | - | `NotFoundPage.vue` | 已实现基础 404 |

守卫逻辑直接读取 `localStorage.token`，没有在路由进入时调用后端验证 token。

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
| `NotFoundPage.vue` | 基础 404 和返回首页链接 |

### 7.2 占位

公开端：

- 首页
- 分类浏览
- 搜索
- 文章详情
- About
- Project
- Markdown 编辑器

管理端：

- 文章列表
- 文章编辑
- 分类管理
- 标签管理
- 站点设置

所有占位页面只渲染标题和“待实现”文本。

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

2026-09-10 执行 `npm run build` 成功，Vite 8.1.0 完成生产构建。

---

## 9. 下一步实现顺序

1. 补齐 Markdown 渲染和安全配置。
2. 实现文章详情、首页、分类列表和搜索。
3. 实现文章管理列表和编辑表单。
4. 实现分类、标签和站点配置管理。
5. 实现本地 Markdown 编辑器和图片上传交互。
6. 补齐修改密码入口和 API。
7. 增加加载、空数据、错误状态和移动端布局。
