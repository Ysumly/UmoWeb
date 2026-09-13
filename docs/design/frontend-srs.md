# UmoWeb 前端规格与实现状态

> 基线日期: 2026-09-13
> 架构: 纯 Vue 3 SPA，不包含 SSR/SSG
> 原则: 已经实现的代码与规划中的产品目标分别标记
> 当前版本: 公开端、公开在线编辑器和管理端核心业务页均已实现

---

## 1. 产品定位

UmoWeb 是单管理员个人博客，包含公开阅读端和秘密路径管理端。

公开端目标：

- 展示站点标题、简介、最新文章和分类。
- 浏览、筛选和搜索已发布内容。
- 渲染 Markdown 文章。
- 阅读 About 和 Project 配置页。
- 提供纯浏览器本地 Markdown 编辑器。

管理端目标：

- 管理员登录和密码修改。
- 文章、分类、标签和站点配置管理。
- Markdown 内容编辑、单文件 front matter 导入和图片上传。
- 草稿与发布状态管理。

非目标：

- 用户注册、评论、多用户、RBAC。
- SSR、搜索引擎优化。

---

## 2. 页面与实现状态

### 2.1 公开端

| 页面 | 路由 | 目标能力 | 当前状态 |
|---|---|---|---|
| 首页 | `/` | 站点信息、最新文章、分类入口 | 已接入真实 API |
| 分类浏览 | `/library` | 分类、标签筛选和分页 | 已接入真实 API |
| 搜索 | `/search` | URL 同步、关键词搜索、429 提示 | 已接入真实 API |
| 文章详情 | `/post/:slug` | Markdown、代码高亮、元信息 | 已接入真实 API |
| About | `/about` | 读取并渲染 `about_page` | 已接入真实 API |
| Project | `/project` | 读取并渲染 `project_page` | 已接入真实 API |
| 在线编辑器 | `/editor` | 导入、编辑、预览、下载 `.md` | 已实现本地草稿 |
| 404 | 任意未匹配路由 | 错误提示和返回首页 | 已实现 |

### 2.2 管理端

管理入口固定为 `/secret-admin`。

| 页面 | 路由 | 目标能力 | 当前状态 |
|---|---|---|---|
| 登录 | `/secret-admin/login` | 登录、存储 token、跳转 | 已实现 |
| 文章列表 | `/secret-admin/contents` | 筛选、分页、编辑、删除 | 已接入真实 API |
| 新建文章 | `/secret-admin/contents/new` | 元信息、Markdown 导入、图片上传 | 已接入真实 API |
| 编辑文章 | `/secret-admin/contents/:id/edit` | 预填、更新、发布/草稿 | 已接入真实 API |
| 分类管理 | `/secret-admin/categories` | 树形 CRUD | 已接入真实 API |
| 标签管理 | `/secret-admin/tags` | 列表 CRUD | 已接入真实 API |
| 站点设置 | `/secret-admin/options` | 站点标题、页面 Markdown | 已接入真实 API |
| 修改密码 | `/secret-admin/password` | 修改管理员密码 | 已接入真实 API |

---

## 3. API 对接约束

前端通过 Axios 使用相对前缀 `/api`。

公开端使用的 8 个接口：

| 方法 | 路径 |
|---|---|
| GET | `/public/site-info` |
| GET | `/public/pages/about` |
| GET | `/public/pages/project` |
| GET | `/public/categories` |
| GET | `/public/tags` |
| GET | `/public/contents` |
| GET | `/public/contents/{slug}` |
| GET | `/public/contents/search` |

管理端当前已封装的 19 个函数覆盖：

- 登录。
- 文章 CRUD。
- 分类 CRUD。
- 标签 CRUD。
- 图片上传。
- 站点配置查询和更新。

修改密码已接入独立受保护页面；成功后清理本地 token，要求使用新密码重新登录。

后端正常响应不套 `{ code, data }`。错误响应才使用 `{ code, message }`。

### 3.1 当前公开端边界

- 首页、书库、搜索、文章详情、About 和 Project 使用现有公开 API，不再使用运行时静态 fixtures。
- 书库筛选和分页由后端执行，`tagId` 精确匹配；`categoryId` 默认精确匹配，启用
  `includeDescendants` 后包含全部后代。
- 主题支持亮色与暗色，使用 `data-theme` 和 `localStorage` 持久化。
- `marked` 页面渲染会转义原始 HTML；代码高亮只注册 Java、JavaScript、SQL 和 Bash。
- 搜索采用显式提交，429 时按后端等待秒数倒计时。
- 公开端在线编辑器不调用后端，草稿使用 `localStorage`；管理端核心业务页均接入真实 API。

---

## 4. 需要的交互行为

### 4.1 搜索

- 输入后点击搜索或按回车提交，不使用逐字自动请求。
- 后端不是全文搜索，只匹配标题和摘要。
- 同一 IP 10 秒内第二次请求返回 429。
- 页面提示等待时间并在倒计时结束前禁用重复提交。

### 4.2 文章编辑

- 表单字段：`title`、`slug`、`summary`、`type`、`status`、`categoryIds`、`tagIds`、`metadata`、`body`。
- `metadata` 后端接收 JSON 字符串，前端应在提交前序列化。
- 新建成功后后端返回 200，不应只接受 201。
- 图片上传成功后拼接返回的 `/images/...` URL。
- 分类和标签接口返回的对象字段与文章表单所需 ID 不同，需要显式映射。
- 已实现选择上传、拖拽和剪贴板图片，插入位置使用当前 textarea 光标。
- 新建文章支持单文件 `.md`/`.markdown` 导入；YAML front matter 可预填字段，缺失时回退到 H1、文件名和默认值。
- 导入只预填现有表单，最终仍调用文章创建接口；相对图片引用只显示警告，不自动上传，也不覆盖已有 slug。
- 桌面端编辑与预览并排，窄屏通过“编辑/预览”切换。
- 未保存内容离开路由或关闭页面时会要求确认。

### 4.3 在线编辑器

- 纯浏览器运行，不调用后端保存。
- 支持读取和下载 `.md`，文件名规范化为安全 basename 并补充 `.md`。
- 已有内容时，导入前必须确认替换。
- 草稿写入 `localStorage["umo-editor-draft-v1"]`，刷新或重新进入时恢复；清空会同步删除草稿。
- 使用 marked 渲染，使用 highlight.js 高亮。
- marked 配置应避免渲染不受信任的原始 HTML。

### 4.4 管理认证

- token 存在 `localStorage`。
- 路由守卫只检查本地 token，不预验证 JWT。
- 业务请求返回 401 时由 Axios 拦截器清理 token 并跳转登录。
- 登录和改密页面的“旧密码错误”由页面就地展示；token 过期等其他 401 仍会清理会话。

---

## 5. 响应式要求

| 场景 | 桌面端 | 移动端 |
|---|---|---|
| 导航 | 水平导航或常驻布局 | 可折叠或紧凑布局 |
| 分类浏览 | 分类侧栏 + 内容区 | 单列，筛选置顶 |
| Markdown 编辑 | 编辑/预览并排 | 编辑/预览切换 |
| 管理端 | 侧栏 + 内容区 | 单列，导航可折叠 |
| 卡片列表 | 多列或列表 | 单列 |

`AdminLayout.vue` 已实现桌面侧栏和移动抽屉导航；文章编辑器在窄屏切换编辑/预览。

---

## 6. 验收基线

### 6.1 当前可验证

- 首页、书库、搜索、文章详情、About、Project 和 404 与真实 API 契约一致。
- 亮暗主题可以切换并在刷新后保持。
- 书库类型、分类、标签筛选和服务端分页可用。
- 选择分类时书库 URL 同步 `includeDescendants=true`，结果包含仅关联子分类的内容；
  显式 `includeDescendants=false` 保持精确匹配。
- Markdown 标题、代码块、表格和引用可以按统一排版渲染。
- 移动导航、390px 布局和公开端主路径无横向溢出。
- `npm run build` 成功。
- `/secret-admin/login` 可调用真实登录 API。
- 无效凭证显示后端错误消息。
- 管理路由无 token 时跳转登录。
- 401 会清理 token。
- 分类和标签支持树形/列表 CRUD，并展示关联内容或子分类的 409 提示。
- 站点设置支持四项配置统一保存、Markdown 预览和部分失败反馈。
- 修改密码成功后旧 token 失效并跳转登录页提示。
- 在线编辑器支持 `.md` 导入/下载、实时预览、本地草稿恢复、移动端切换和安全 Markdown 渲染。
- 管理端新建文章支持 YAML front matter 导入、无 front matter 回退、字段错误提示、相对图片警告和重复 slug 就地修正。

### 6.2 浏览器回归

- Playwright functional 用例覆盖公开端错误态、429、390px 布局及管理端核心流程。
- visual-desktop 和 visual-mobile 保存 14 张核心页面截图基线。
- 浏览器套件使用 Mock API，本机 Chrome 和 Windows 基线；真实后端继续由 MySQL 冒烟脚本验证。

当前已有 60 个 Node 内置测试；现代 Playwright 套件包含每个平台 40 个浏览器检查
（26 functional + 14 visual）。尚无组件级单元测试框架。
