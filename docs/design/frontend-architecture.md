# UmoWeb 前端架构

> 基线日期: 2026-09-14
> 项目路径: `Client Side/umo-web-frontend/`
> 状态: 公开端阅读增强、在线编辑器、隐私说明、四款训练游戏和管理端核心业务页均已实现

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
| yaml | 2.9.x | 管理端 Markdown front matter 解析 |
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
│   ├── games.spec.js
│   ├── public.spec.js
│   └── visual.spec.js
├── public/
│   ├── favicon.png
│   ├── umo-logo.png
│   ├── privacy-config.json
│   └── icons.svg
└── src/
    ├── main.js
    ├── App.vue
    ├── style.css
    ├── directives/
    │   └── reveal.js
    ├── config/
    │   ├── contentTypes.js
    │   └── games.js
    ├── games/
    │   ├── gameLogic.js
    │   ├── gameLogic.test.js
    │   └── games.css
    ├── layouts/
    │   └── PublicLayout.vue
    ├── theme/
    │   ├── theme.js
    │   └── useTheme.js
    ├── utils/
    │   ├── accessPrivacy.js
    │   ├── adminContent.js
    │   ├── apiError.js
    │   ├── articleOutline.js
    │   ├── articleOutline.test.js
    │   ├── editor.js
    │   ├── format.js
    │   ├── markdownImport.js
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
    │   │   ├── ArticleOutline.vue
    │   │   ├── ArticleOutlineList.vue
    │   │   ├── ContentCard.vue
    │   │   ├── ContentState.vue
    │   │   ├── MarkdownArticle.vue
    │   │   ├── SectionHeading.vue
    │   │   ├── SiteFooter.vue
    │   │   └── SiteHeader.vue
    │   ├── games/
    │   │   ├── GameResultDialog.vue
    │   │   └── GameShell.vue
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
        │   ├── PrivacyPage.vue
        │   ├── GamesPage.vue
        │   ├── StroopGamePage.vue
        │   ├── DigitSpanGamePage.vue
        │   ├── PokerMemoryGamePage.vue
        │   ├── SchulteGamePage.vue
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
| `/privacy` | `privacy` | `PrivacyPage.vue` | 读取运行时访问保留策略 |
| `/games` | `games` | `GamesPage.vue` | 四款游戏入口 |
| `/games/stroop` | `game-stroop` | `StroopGamePage.vue` | 纯浏览器游戏 |
| `/games/digit-span` | `game-digit-span` | `DigitSpanGamePage.vue` | 纯浏览器游戏 |
| `/games/poker-memory` | `game-poker-memory` | `PokerMemoryGamePage.vue` | 纯浏览器游戏 |
| `/games/schulte` | `game-schulte` | `SchulteGamePage.vue` | 纯浏览器游戏 |
| `/secret-admin` | - | `AdminLayout.vue` | 重定向到文章页 |
| `/secret-admin/login` | `login` | `LoginPage.vue` | 已实现 |
| `/secret-admin/contents` | `admin-contents` | `ContentListPage.vue` | 已接入真实 API |
| `/secret-admin/contents/new` | `content-new` | `ContentEditPage.vue` | 已接入真实 API 和 Markdown 导入 |
| `/secret-admin/contents/:id/edit` | `content-edit` | `ContentEditPage.vue` | 已接入真实 API |
| `/secret-admin/categories` | `admin-cats` | `CategoryManagePage.vue` | 已接入真实 API |
| `/secret-admin/tags` | `admin-tags` | `TagManagePage.vue` | 已接入真实 API |
| `/secret-admin/options` | `admin-options` | `OptionPage.vue` | 已接入真实 API |
| `/secret-admin/password` | `admin-password` | `ChangePasswordPage.vue` | 已接入真实 API |
| `/:pathMatch(.*)*` | `not-found` | `NotFoundPage.vue` | 已实现 |

守卫逻辑直接读取 `localStorage.token`，没有在路由进入时调用后端验证 token。

公开路由通过 `meta.motion` 区分 `cinematic` 和 `focused` 动效等级。
游戏路由额外设置 `instantTransition`，不渲染全屏 route wipe，并以 `section: games`
维持主导航激活状态。

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
| `api/admin.js` | 21 | 21 个管理端函数，包含图片列表、图片删除和修改密码 |

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
| `ContentEditPage.vue` | 新建/编辑、Markdown front matter 导入、分类标签、metadata、分屏预览、图片上传和未保存保护 |
| `CategoryManagePage.vue` | 分类树筛选、父级/排序字段、增改删、409 提示和未保存保护 |
| `TagManagePage.vue` | 标签增改删、字段校验、409 提示和未保存保护 |
| `ImageManagePage.vue` | 图片缩略图、引用筛选、分页、删除确认、409 提示和列表刷新 |
| `OptionPage.vue` | 站点信息、About/Project Markdown 预览、统一保存和部分失败反馈 |
| `ChangePasswordPage.vue` | 密码校验、修改后清 token、跳转登录页 |
| `EditorPage.vue` | `.md` 导入/下载、Markdown 编辑与安全预览、移动端切换和本地草稿恢复 |
| `PrivacyPage.vue` | 展示收集目的、六字段边界、实际保留期、管理员访问边界和第三方限制 |
| `GamesPage.vue` | 展示四款训练入口，不调用 API |
| 四款游戏页面 | 保留原规则、键盘/触控操作、结果结算和旧版本地成绩 |
| `NotFoundPage.vue` | 公开端视觉样式，提供返回首页和书库入口 |

### 7.2 公开端真实 API

以下页面读取现有公开接口，并提供加载、空数据、错误和重试状态：

- 首页：站点介绍、主推文章、最新内容、类型入口和 About 预览。
- 书库：类型、分类、标签服务端筛选和分页；分类筛选默认包含全部后代。
- 搜索：显式提交、URL 同步和 429 倒计时。
- 文章详情：Markdown、代码高亮、分类标签、后端返回的前后文章、自动目录、滚动章节高亮和
  阅读进度；桌面端目录位于侧栏，980px 以下使用可关闭的悬浮目录。
- About 与 Project：分别读取配置页 Markdown。

主题由 `data-theme` 控制，亮暗偏好保存在 `localStorage`。公开端支持首页电影化动效、滚动揭示和减少动态偏好。

### 7.3 公开在线编辑器

`EditorPage.vue` 不调用后端，使用原生 `textarea` 和 `MarkdownArticle` 完成分屏编辑/预览；窄屏切换单栏。支持导入与下载 `.md`、文件名规范化、导入替换确认和清空；草稿写入 `localStorage["umo-editor-draft-v1"]`，页面重新进入时恢复。

### 7.4 训练游戏

游戏中心和四款游戏均位于公开端，不调用后端。共享 `GameShell` 负责标题、返回和游戏区域，
`GameResultDialog` 提供焦点、`aria-modal`、Escape 和结果结算；纯逻辑集中在
`src/games/gameLogic.js`。

Stroop 保留 84 试次和 25% 一致试次，使用 `stroop_84_parchment`；五色文字直接使用对应
按钮色，黑色刺激在亮暗主题分别使用略暗、略亮于背景的描边，并忽略键盘自动重复；
倒背数字保留 4 位起步、每级三题答对两题升级，不保存成绩；扑克牌保留两张开局、
三秒记忆和 `poker_memory_best_span`；舒尔特保留 3×3 至 10×10 和
`schulte_parchment_best` 分尺寸最佳成绩。游戏路由即时进入，非规则反馈等待已压缩。

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

2026-09-14 执行 `npm test`、`npm run build` 和完整 Windows Playwright 测试成功。
搜索覆盖标题、摘要和 Markdown 正文，正文命中时结果卡片展示 `excerpt`。当前 82 个 Node 测试覆盖路由、
管理路径、主题解析、访问隐私配置、管理端文章/分类/标签/站点/改密规则、编辑器草稿与文件规则、
Markdown front matter 导入、书库后代参数、文章目录树与展开状态、标题 ID/旧锚点兼容、
游戏规则与旧成绩、Markdown 原始 HTML、危险 URL 协议和图片 alt 转义；
Playwright 另含 46 个 functional 和 28 个视觉检查。
书库选择分类时 URL 使用
`category=<id>&includeDescendants=true`，显式 `false` 仍可请求精确匹配。

---

## 9. 下一步实现顺序

当前已提供 Docker Compose 全栈入口，前端由 Nginx 提供静态构建并代理 `/api`、`/images`；
详细命令见 `docs/project/docker-guide.md`。后续工作按 Post-v1 路线推进：

1. 第一阶段完成正式数据、HTTPS、备份恢复和上线回滚。
2. 第二阶段已接入 CI、Linux Playwright、真实 MySQL 集成、本地版本化镜像发布/回滚和
   自托管访问统计，`v1.0.0-rc.5` 已通过 ECS 发布和 29/29 验收。
3. 第三阶段已完成并合并 Markdown 导入、子分类筛选、图片删除、正文全文搜索和四个训练游戏，
   阶段出口条件已满足。
4. 第四阶段实现非 AI 阅读体验、批量管理和定时发布，不包含文章修订历史。
5. 第五阶段在需求、隐私和成本明确后评估管理端 AI；AI 草稿和转换结果只保存在浏览器本地。
6. 公开语义搜索、原文问答和知识图谱继续后置，作为独立项目重新评审。

完整路线见
[`docs/superpowers/plans/2026-09-14-post-v1-roadmap.md`](../superpowers/plans/2026-09-14-post-v1-roadmap.md)。
第一至第三阶段的历史记录见
[`docs/superpowers/plans/2026-09-12-four-phase-roadmap.md`](../superpowers/plans/2026-09-12-four-phase-roadmap.md)。
