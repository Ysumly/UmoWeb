# UmoWeb 接口与构建测试指南

> 基线日期: 2026-09-10
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

后端 `pom.xml` 的编译目标仍是 Java 17。

### 1.2 环境变量

```powershell
$env:DB_USER = "root"
$env:DB_PASS = "<本机 MySQL 密码>"
$env:JWT_SECRET = "<至少 32 字符的本地 secret>"
```

可选：

```powershell
$env:INIT_ADMIN_USER = "admin"
$env:INIT_ADMIN_PASS = "admin123"
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

`users` 表为空时，后端会根据 `app.init.*` 自动创建管理员，默认：

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

当前 `BoundaryTest` 有 20 个 MockMvc 测试，不连接 MySQL。`UmoWebBackendApplicationTests` 只是一条空测试，不会加载完整 Spring Context。

### 2.2 前端

```powershell
cd "Client Side\umo-web-frontend"
npm run build
```

2026-09-10 已验证：

- 后端 21 个测试全部通过，其中 20 个是 `BoundaryTest`，1 个是占位测试。
- Vite 8.1.0 前端生产构建成功。

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

存在时预期 200，并包含 `body`。当前实现不会组装详情分类和标签，因此 `categories`、`tags` 为 `null`。如果 Markdown 文件缺失，仍返回 200，但 `body` 为 `""`。

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

预期 204。

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

预期 200。列表包含草稿；详情包含 `body`。

### 6.7 编辑文章

```http
PUT {{baseUrl}}/api/admin/contents/1
```

请求体同新建。修改 slug 后会移动 Markdown 文件。预期 200。

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

`file` 选择 png/jpg/gif/webp。

预期 200：

```json
{
  "id": 1,
  "url": "/images/2026/09/uuid.png",
  "originalName": "image.png",
  "size": 12345
}
```

上传文本文件预期 400。超过 50MB 预期 413。

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

文档目标是有关联内容时返回 409，但当前实现查询方向错误，不能可靠拦截。测试当前版本时不要把这个作为可靠回归条件。

### 8.3 分类筛选

```text
categoryId=父分类 ID
```

不会返回子分类内容。

### 8.4 搜索范围

修改 Markdown 正文但保持 title/summary 不变时，搜索结果不会变化。

### 8.5 分页边界

`page` 和 `size` 没有校验。当前不应假设 `page=0`、`size=0` 或负值会被安全处理。

### 8.6 文件失败

Markdown 文件缺失时详情返回 200 和空 body。文章更新在文件移动过程中也没有跨数据库事务的一致性。

完整风险见 [audit-log.md](audit-log.md)。
