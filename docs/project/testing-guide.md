# UmoWeb 接口与构建测试指南

> 基线日期: 2026-09-11
> 接口数: 公开 8 个，管理 19 个，共 27 个
> 关键约定: 正常响应没有 `{ code, data }` 包装层

---

## 1. 环境准备

### 1.1 运行环境

当前开发机可用：

| 工具 | 版本 |
|---|---|
| Java | 21.0.6 |
| Maven | 3.9.11 |
| Node.js | 24.12.0 |
| npm | 11.6.2 |
| Playwright Test | 1.63.0 |

后端 `pom.xml` 的编译目标仍是 Java 17。

### 1.2 环境变量

```powershell
$env:DB_USER = "root"
$env:DB_PASS = "<本机 MySQL 密码>"
```

开发默认 profile 为 `dev`，允许项目自带默认 JWT/管理员值以便本地启动。生产必须：

```powershell
$env:SPRING_PROFILES_ACTIVE = "prod"
$env:JWT_SECRET = "<至少 32 字符的独立 secret>"
$env:INIT_ADMIN_USER = "<管理员用户名>"
$env:INIT_ADMIN_PASS = "<强管理员密码>"
$env:CORS_ALLOWED_ORIGINS = "https://<正式域名>"
```

### 1.3 数据库

```powershell
mysql -u root -p umo_blog
```

```sql
SOURCE docs/design/schema.sql;
SOURCE docs/design/seed-data.sql;
```

执行路径需按当前工作目录调整。

### 1.4 启动后端

```powershell
cd "Server Side\UmoWebBackend"
mvn spring-boot:run
```

如果机器级 Maven `settings.xml` 的仓库路径不可写，应通过 `-gs` 和 `-s` 指定隔离的临时 settings 文件。

项目自带 `mvnw.cmd`，但当前 Windows/PowerShell 环境的 wrapper 启动曾失败；优先使用系统 `mvn`。

开发环境 `users` 表为空时，后端会根据 `app.init.*` 自动创建管理员，默认：

```text
username: admin
password: admin123
```

不需要手动插入固定 BCrypt hash。

### 1.5 启动前端

```powershell
cd "Client Side\umo-web-frontend"
npm install
npm run dev
```

访问 `http://localhost:5173`。

---

## 2. 自动化构建

### 2.1 后端

```powershell
cd "Server Side\UmoWebBackend"
mvn test
```

当前完整测试共 83 个，包含 `BoundaryTest`、文件/路径工具、VO 批量组装、JWT、
Mapper XML 别名解析、构造器注入、Jackson 自动配置、拦截器和登录限流测试。MockMvc 边界测试不连接 MySQL；
`UmoWebBackendApplicationTests` 仍是一条空测试，不会加载完整 Spring Context。

### 2.2 数据库迁移副本 + 全接口冒烟

2026-09-11 使用独立临时 MySQL 5.7 实例完成真实副本演练：

- 使用提交 `8369ac0` 的旧版 `schema.sql` 建立 8 张表和 6 篇内容。
- 额外注入 4 条孤儿关联和 1 条悬空父分类，验证迁移清理路径。
- `mysqldump` 生成 12,881 字节备份，SHA-256 为
  `70C8E7E33DB2815EFF5BB17EE1E5FA620AB594BE4E81F2D013E023A96E4C7C9A`。
- 将备份还原为 `umo_blog_copy`，仅对副本执行兼容迁移，并连续执行两次验证幂等性。
- 迁移后 `token_version` 1 个、新增索引 3 个、新增外键 5 个；孤儿关联和悬空父级均为 0。
- 源库仍无 `token_version`，证明演练没有触碰源库。

后端连接 `umo_blog_copy` 启动后，执行：

```powershell
cd "Server Side\UmoWebBackend"
.\scripts\api-smoke.ps1 `
  -BaseUrl "http://127.0.0.1:18080" `
  -Username "smoke_admin" `
  -Password "<current-password>"
```

脚本覆盖公开端 8 个和管理端 19 个接口，结果为 `27/27` 通过；同时验证：

- 无有效 JWT 的管理端请求返回 401。
- 修改密码返回 204，旧 token 立即失效。
- 搜索首次返回 200，10 秒内重复请求返回 429。
- 详情 `previous` 为更早文章、`next` 为更新文章，首尾边界为 `null`。
- PNG 上传同时通过 MIME 和文件签名校验。
- 公开列表只返回 `PUBLISHED`；管理列表和详情同时暴露 `DRAFT` 与 `PUBLISHED` 状态。
- 测试创建的分类、标签、草稿文章全部删除，密码和 `site_title` 恢复原值。

脚本运行前要求后端已启动并使用真实 MySQL。脚本会临时修改管理员密码和 `site_title`，
最后恢复；由于当前没有图片删除接口，上传的测试图片会保留在测试库和存储目录中。

### 2.3 前端

```powershell
cd "Client Side\umo-web-frontend"
npm run build
npm test
npm run test:e2e
```

2026-09-12 已验证：

- Vite 8.1.0 前端生产构建成功。
- 前端 46 个 Node 测试通过，覆盖路由、管理路径、主题、管理端文章/分类/标签/站点/改密规则、编辑器草稿与文件规则、API 错误解析、日期格式、查询规范化和 Markdown 安全。
- Playwright 34 个浏览器检查通过，其中 20 个 functional 用例覆盖公开端、在线编辑器和全部管理端核心流程，14 个视觉断言覆盖核心页面的桌面与 390px 基线。
- 浏览器 E2E 通过可控 Mock API 运行，不依赖 MySQL 或 Spring Boot；真实接口仍由第 2.2 节的 MySQL 副本与 `api-smoke.ps1` 验证。

### 2.4 Playwright 浏览器回归

```powershell
cd "Client Side\umo-web-frontend"
npm run test:e2e
```

执行流程：

1. 先运行 `npm run build` 生成生产构建。
2. `e2e/runPlaywright.js` 在 `http://127.0.0.1:4173` 启动轻量 Node 静态服务器并运行 Playwright；测试结束后关闭服务器，避免 Windows 上 Vite preview 残留进程。
3. 使用本机稳定版 Chrome channel，不下载独立 Playwright Chromium。
4. 浏览器级路由拦截 `/api/**`，每个测试使用独立的状态化 Mock API。
5. functional 项目覆盖公开阅读、在线编辑器、管理端认证与 CRUD，以及 390px 布局。
6. visual-desktop 和 visual-mobile 项目比较 14 张页面截图。

更新视觉基线：

```powershell
npm run test:e2e:update
```

运行前端全部验证：

```powershell
npm run test:all
```

视觉基线位于 `e2e/visual.spec.js-snapshots/`。当前基线只在 Windows 与本机稳定 Chrome 下生成；切换到 Linux 或其他浏览器后应重新生成并审查。

### 2.5 Docker 全栈

首次启动和日常命令见 [docker-guide.md](docker-guide.md)。核心验证命令：

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\docker-up.ps1
docker compose --env-file .env.docker ps
```

2026-09-12 在本机验证：

- MySQL 8.4、后端和 Nginx 三个容器均达到健康状态。
- 首次数据卷初始化后公开列表返回 5 篇已发布文章，管理端可见 6 篇内容（含 1 篇草稿）。
- 首页、`/library` SPA 深链接和演示文章 Markdown 正文通过 Nginx 正常加载。
- 通过 Nginx 入口执行 `api-smoke.ps1`，27/27 接口通过。
- 站点副标题和文章摘要中文显示正常。
- 2MB PNG 经 Nginx 上传返回 200，后端保存文件大小一致。
- 重启容器但不删除命名卷后，文章、管理员和上传图片仍然存在。

### 2.6 备份与恢复

Bash 单元测试：

```bash
bash scripts/backup/tests/backup-unit.sh
```

覆盖保留数量和 8GiB 容量上限、恢复项目名护栏、外层/内部校验和及导出后校验。

生产或隔离环境的端到端验证顺序：

```bash
/opt/umoweb/scripts/backup/create-backup.sh
/opt/umoweb/scripts/backup/verify-backup.sh /opt/umoweb/backups/umoweb-backup-<时间>.tar.gz
/opt/umoweb/scripts/backup/restore-backup.sh /opt/umoweb/backups/umoweb-backup-<时间>.tar.gz
```

执行恢复栈的 `api-smoke.ps1`，要求 27/27 通过，然后：

```bash
/opt/umoweb/scripts/backup/cleanup-restore.sh umoweb-restore-<时间>
```

2026-09-12 已验证：

- Bash 单元测试全部通过。
- 本地隔离源栈完成一致备份，归档包含 6 个 app 文件、5 篇 Markdown 和完整 8 张表元数据。
- 从空环境恢复后表行数和逐文件 SHA-256 清单一致，恢复栈 27/27 接口冒烟通过。
- ECS systemd 服务手动执行成功，`frontend`、`backend`、`mysql` 在备份后全部恢复为 healthy。
- 将 ECS 归档下载到开发机后可跨主机恢复，恢复栈再次执行 27/27 通过。

---

## 3. Apifox 环境

建议变量：

| 变量 | 值 |
|---|---|
| `baseUrl` | `http://localhost:8080` |
| `token` | 登录后脚本写入 |

管理端集合统一添加 Header：

```text
Authorization: Bearer {{token}}
```

登录请求单独去掉 Header。

登录后置脚本：

```javascript
const body = pm.response.json();
if (body.token) {
  pm.environment.set("token", body.token);
}
```

注意：token 在响应根节点，不在 `body.data.token`。

---

## 4. 公开端测试

### 4.1 站点信息

```http
GET {{baseUrl}}/api/public/site-info
```

预期 200，根对象包含：

```json
{
  "siteTitle": "Umo Blog",
  "siteSubtitle": "代码 · 阅读 · 创作",
  "aboutHtml": "## 关于我\n...",
  "projectHtml": "## 项目\n..."
}
```

### 4.2 About 页面

```http
GET {{baseUrl}}/api/public/pages/about
```

预期 200：

```json
{
  "content": "## 关于我\n..."
}
```

### 4.3 Project 页面

```http
GET {{baseUrl}}/api/public/pages/project
```

预期 200：

```json
{
  "content": "## 项目\n..."
}
```

### 4.4 分类树

```http
GET {{baseUrl}}/api/public/categories
GET {{baseUrl}}/api/public/categories?type=NOTE
```

预期 200，空库为 `[]`，有数据时是顶层分类数组。

### 4.5 标签列表

```http
GET {{baseUrl}}/api/public/tags
```

预期 200，空库为 `[]`。

### 4.6 已发布文章列表

```http
GET {{baseUrl}}/api/public/contents?page=1&size=10
```

预期 200：

```json
{
  "items": [],
  "page": 1,
  "size": 10,
  "total": 0
}
```

可继续测试：

```text
type=NOTE
categoryId=<分类 ID>
tagId=<标签 ID>
sort=created_at_desc
```

注意：列表不返回草稿；`categoryId` 不包含子分类。

### 4.7 文章详情

不存在：

```http
GET {{baseUrl}}/api/public/contents/no-such-slug
```

预期 404：

```json
{
  "code": 404,
  "message": "Content not found: no-such-slug"
}
```

存在时预期 200，并包含 `body`、`categories`、`tags`、`previous` 和 `next`。如果 Markdown 文件缺失，
仍返回 200，但 `body` 为 `""`。

`previous` 指向更早发布的内容，`next` 指向更晚发布的内容；边界为 `null`。同发布时间下，
小 ID 为更早，大 ID 为更晚。

### 4.8 搜索

```http
GET {{baseUrl}}/api/public/contents/search?q=java&page=1&size=10
```

预期 200，结构与文章列表相同。

搜索只匹配：

- `title`
- `summary`

不匹配 Markdown 正文。

限流回归：

1. 第一次请求预期 200。
2. 10 秒内再次请求预期 429。
3. 响应消息形如 `Too many requests. Please wait N seconds.`。

不要用伪造 `X-Forwarded-For` 绕过限流；默认只有 `remoteAddr` 参与限流，
直连地址还需匹配 `TRUSTED_PROXIES` 中的精确 IP 或 CIDR 才读取转发头。

### 4.9 公开在线编辑器

访问 `/editor`，不需要登录，也不调用后端接口。验证：

1. 编辑 Markdown 后桌面同时更新预览，390px 下可在编辑/预览标签间切换。
2. 刷新页面后恢复内容和文件名；清空后草稿删除。
3. 内容非空时导入 `.md` 前出现替换确认；非 Markdown 扩展名被拒绝。
4. 下载文件名为规范化后的 `.md`，正文保持 UTF-8。
5. 原始 HTML 被转义，`javascript:` 等危险链接不会进入可执行 HTML。

---

## 5. 管理端认证

### 5.1 登录

```http
POST {{baseUrl}}/api/admin/login
Content-Type: application/json
```

```json
{
  "username": "admin",
  "password": "admin123"
}
```

预期 200：

```json
{
  "token": "eyJ...",
  "expiresAt": "2026-09-11T10:00:00"
}
```

同一用户名/IP 连续失败 5 次后，后续登录在 15 分钟窗口内预期 429。

### 5.2 无 JWT 拦截

```http
GET {{baseUrl}}/api/admin/contents
```

不带 Header 时预期 401：

```json
{
  "code": 401,
  "message": "Missing or invalid Authorization header"
}
```

### 5.3 修改密码

```http
PUT {{baseUrl}}/api/admin/change-password
```

```json
{
  "oldPassword": "admin123",
  "newPassword": "newPass666"
}
```

预期 204。旧 token 在修改密码后再次访问管理端预期 401。

---

## 6. 管理端内容测试

### 6.1 新建分类

```http
POST {{baseUrl}}/api/admin/categories
```

```json
{
  "name": "Java",
  "slug": "java",
  "type": "NOTE",
  "sortOrder": 1
}
```

预期 200，不是 201。

### 6.2 新建子分类

```json
{
  "name": "Spring",
  "slug": "spring",
  "parentId": 1,
  "type": "NOTE",
  "sortOrder": 1
}
```

### 6.3 分类树和详情

```http
GET {{baseUrl}}/api/admin/categories
GET {{baseUrl}}/api/admin/categories/1
```

预期 200。

### 6.4 新建标签

```http
POST {{baseUrl}}/api/admin/tags
```

```json
{
  "name": "Java",
  "slug": "java"
}
```

预期 200。

### 6.5 新建文章

```http
POST {{baseUrl}}/api/admin/contents
```

```json
{
  "title": "Java 集合框架详解",
  "slug": "java-collections",
  "body": "# Java 集合框架\n\n正文",
  "summary": "深入理解 Java 集合框架",
  "type": "NOTE",
  "status": "PUBLISHED",
  "categoryIds": [1],
  "tagIds": [1],
  "metadata": "{\"readingTime\":15,\"difficulty\":\"intermediate\"}"
}
```

预期 200，返回 `ContentDetailVO`。

### 6.6 管理端文章列表与详情

```http
GET {{baseUrl}}/api/admin/contents
GET {{baseUrl}}/api/admin/contents/1
```

预期 200。列表包含草稿与已发布文章，并通过 `status` 字段区分；详情包含 `status` 和 `body`。

### 6.7 编辑文章

```http
PUT {{baseUrl}}/api/admin/contents/1
```

请求体同新建。修改 slug 后会写临时文件、数据库成功后原子替换，并在提交后清理旧文件。预期 200。

### 6.8 删除文章

```http
DELETE {{baseUrl}}/api/admin/contents/1
```

预期 204。

---

## 7. 图片和站点配置

### 7.1 上传图片

```http
POST {{baseUrl}}/api/admin/images/upload
Content-Type: multipart/form-data
```

`file` 选择 png/jpg/gif/webp，且文件内容签名必须与 MIME 匹配。

预期 200：

```json
{
  "id": 1,
  "url": "/images/2026/09/uuid.png",
  "originalName": "image.png",
  "size": 12345
}
```

上传文本文件、伪造 `Content-Type` 或伪造扩展名预期 400。超过 50MB 预期 413。

### 7.2 查询配置

```http
GET {{baseUrl}}/api/admin/options
```

预期 200，直接返回 KV Map。

### 7.3 更新配置

```http
PUT {{baseUrl}}/api/admin/options/site_title
```

```json
{
  "value": "Umo 的博客"
}
```

预期 204。

---

## 8. 当前边界与已知缺陷

### 8.1 新建资源

所有 POST 新建接口成功时都返回 200。不要把 201 作为当前预期。

### 8.2 分类/标签删除保护

有关联内容时返回 409；删除仍有子分类的父分类也返回 409。

### 8.3 分类筛选

```text
categoryId=父分类 ID
```

不会返回子分类内容。

### 8.4 搜索范围

修改 Markdown 正文但保持 title/summary 不变时，搜索结果不会变化。

### 8.5 分页边界

`page < 1`、`page > 1000000`、`size < 1` 或 `size > 100` 返回 400；
非法 `type/status` 和 metadata 同样返回 400。

### 8.6 文件失败

Markdown 文件缺失时详情返回 200 和空 body。文章创建/更新使用临时文件；
数据库失败会保留或恢复旧状态，数据库提交后的文件清理失败会记录日志并保留可恢复孤儿文件。

完整风险见 [audit-log.md](audit-log.md)。
