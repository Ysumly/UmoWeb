# UmoWeb API 接口参考

> 基线日期: 2026-09-10
> 事实来源: `controller/`、`model/dto/`、`model/vo/`、`GlobalExceptionHandler`、Mapper XML
> 接口总数: 公开端 8 个，管理端 19 个，共 27 个

---

## 1. 通用约定

| 项 | 实际行为 |
|---|---|
| 开发地址 | `http://localhost:8080` |
| 公开前缀 | `/api/public` |
| 管理前缀 | `/api/admin` |
| 认证 | `Authorization: Bearer <token>` |
| JSON 响应 | 正常接口直接返回 VO、数组、Map 或分页对象 |
| 错误响应 | `{ "code": 数字, "message": "文字" }` |
| 分页响应 | `{ "items": [], "page": 1, "size": 10, "total": 0 }` |
| 日期时间 | Jackson JSR-310 默认 ISO-8601，如 `2026-06-20T10:00:00` |

重要：正常响应没有 `{ code, data }` 包装层。

### 1.1 HTTP 状态码

| 状态 | 使用场景 |
|---|---|
| 200 | 查询、登录、新建、编辑成功 |
| 204 | 删除、修改密码、更新配置成功 |
| 400 | 请求体缺失、参数校验失败、图片类型不支持 |
| 401 | 未认证、JWT 无效或过期、用户名密码错误 |
| 404 | 内容、分类、标签不存在 |
| 409 | 唯一键冲突 |
| 413 | Multipart 文件或请求超过 50MB |
| 429 | 搜索同 IP 10 秒内重复请求 |
| 500 | 未处理异常 |

当前代码不会为新建资源返回 201。

---

## 2. 公开端接口

### 2.1 获取站点信息

```http
GET /api/public/site-info
```

直接返回：

```json
{
  "siteTitle": "Umo Blog",
  "siteSubtitle": "代码 · 阅读 · 创作",
  "aboutHtml": "## 关于我\n...",
  "projectHtml": "## 项目\n..."
}
```

说明：`aboutHtml` 和 `projectHtml` 字段名虽然含 `Html`，代码实际返回数据库中的 Markdown 原文。

### 2.2 获取 About 页面

```http
GET /api/public/pages/about
```

```json
{
  "content": "## 关于我\n..."
}
```

配置项不存在时 `content` 为 `""`。

### 2.3 获取 Project 页面

```http
GET /api/public/pages/project
```

```json
{
  "content": "## 项目\n..."
}
```

### 2.4 获取分类树

```http
GET /api/public/categories
GET /api/public/categories?type=NOTE
```

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `type` | string | 否 | `NOTE`、`NOVEL`、`BOOK_REVIEW`；不传返回全部 |

```json
[
  {
    "id": 1,
    "name": "编程",
    "slug": "programming",
    "type": "NOTE",
    "children": [
      {
        "id": 2,
        "name": "Java",
        "slug": "java",
        "type": "NOTE",
        "children": []
      }
    ]
  }
]
```

分类先按 `sort_order ASC, id ASC` 从数据库读取，再在内存中按 `parent_id` 组装树。

### 2.5 获取标签列表

```http
GET /api/public/tags
```

```json
[
  {
    "id": 1,
    "name": "Java",
    "slug": "java"
  }
]
```

### 2.6 获取已发布文章列表

```http
GET /api/public/contents?page=1&size=10&type=NOTE&categoryId=2&tagId=1&sort=published_at_desc
```

| 参数 | 类型 | 默认值 | 实际行为 |
|---|---|---|---|
| `page` | int | 1 | 当前无范围校验 |
| `size` | int | 10 | 当前无范围校验 |
| `type` | string | - | 精确匹配内容类型 |
| `categoryId` | long | - | 仅匹配该分类，不包含子分类 |
| `tagId` | long | - | 精确匹配标签 |
| `sort` | string | `published_at_desc` | 仅严格等于 `created_at_desc` 时按创建时间倒序，其他值按发布时间倒序 |

仅返回 `status=PUBLISHED`。

```json
{
  "items": [
    {
      "id": 1,
      "title": "Spring Boot 快速上手",
      "slug": "spring-boot-quickstart",
      "summary": "从零搭建一个 Spring Boot 项目。",
      "type": "NOTE",
      "categories": [
        {
          "id": 3,
          "name": "Spring",
          "slug": "spring",
          "type": "NOTE",
          "children": null
        }
      ],
      "tags": [
        {
          "id": 2,
          "name": "Spring",
          "slug": "spring"
        }
      ],
      "metadata": {
        "readingTime": 10
      },
      "publishedAt": "2026-06-20T10:00:00"
    }
  ],
  "page": 1,
  "size": 10,
  "total": 1
}
```

`metadata` 数据库中为 JSON 字符串，列表 VO 中会解析为对象；解析失败时返回空对象。

### 2.7 获取已发布文章详情

```http
GET /api/public/contents/{slug}
```

```json
{
  "id": 1,
  "title": "Spring Boot 快速上手",
  "slug": "spring-boot-quickstart",
  "summary": "从零搭建一个 Spring Boot 项目。",
  "type": "NOTE",
  "categories": null,
  "tags": null,
  "metadata": {},
  "publishedAt": "2026-06-20T10:00:00",
  "body": "# Spring Boot\n\nMarkdown 原文..."
}
```

行为：

- 只查询 `PUBLISHED` 内容。
- `slug` 不存在或内容不是已发布状态时返回 404。
- 当前 `getBySlug` 没有组装分类和标签，因此详情中的 `categories`、`tags` 为 `null`；列表接口才有分类/标签数组。
- Markdown 文件读取失败时返回 200，`body` 为 `""`。

### 2.8 搜索已发布文章

```http
GET /api/public/contents/search?q=Spring&page=1&size=10
```

| 参数 | 类型 | 默认值 | 实际行为 |
|---|---|---|---|
| `q` | string | `""` | 匹配 `title LIKE` 或 `summary LIKE` |
| `page` | int | 1 | 当前无范围校验 |
| `size` | int | 10 | 当前无范围校验 |

说明：

- 不搜索 Markdown 正文。
- 无 `q` 时 SQL 使用 `LIKE '%%'`，因此返回全部已发布内容。
- 同 IP 10 秒内只允许一次请求，重复请求返回 429。

错误示例：

```json
{
  "code": 429,
  "message": "Too many requests. Please wait 8 seconds."
}
```

---

## 3. 管理端认证

### 3.1 登录

```http
POST /api/admin/login
Content-Type: application/json
```

请求：

```json
{
  "username": "admin",
  "password": "admin123"
}
```

成功响应：

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "expiresAt": "2026-09-11T10:00:00"
}
```

此接口不经过 `AdminInterceptor`。

### 3.2 修改密码

```http
PUT /api/admin/change-password
Authorization: Bearer <token>
```

请求：

```json
{
  "oldPassword": "admin123",
  "newPassword": "newPass666"
}
```

约束：`newPassword` 至少 6 位。

成功返回 `204 No Content`。

---

## 4. 管理端文章

### 4.1 查询文章列表

```http
GET /api/admin/contents?page=1&size=10&type=NOTE&status=DRAFT&categoryId=2&tagId=1&sort=created_at_desc
Authorization: Bearer <token>
```

参数与公开列表一致，额外支持：

| 参数 | 类型 | 说明 |
|---|---|---|
| `status` | string | `DRAFT` 或 `PUBLISHED`；不传返回全部 |

返回含草稿的分页 `PageResult<ContentListVO>`。

### 4.2 查询文章详情

```http
GET /api/admin/contents/{id}
Authorization: Bearer <token>
```

返回 `ContentDetailVO`，含 Markdown `body`。资源不存在返回 404。

### 4.3 新建文章

```http
POST /api/admin/contents
Authorization: Bearer <token>
Content-Type: application/json
```

请求：

```json
{
  "title": "Spring Boot 快速上手",
  "slug": "spring-boot-quickstart",
  "body": "# Spring Boot\n...",
  "summary": "快速上手指南",
  "type": "NOTE",
  "status": "PUBLISHED",
  "categoryIds": [2, 3],
  "tagIds": [1, 2],
  "metadata": "{\"readingTime\":10,\"difficulty\":\"beginner\"}"
}
```

| 字段 | 类型 | 必填 | 实际行为 |
|---|---|---|---|
| `title` | string | 是 | `@NotBlank` |
| `slug` | string | 是 | `@NotBlank`，数据库唯一 |
| `body` | string | 否 | `null` 时写入空字符串 |
| `summary` | string | 否 | - |
| `type` | string | 是 | `ContentType.valueOf()`，必须为 `NOTE`、`NOVEL`、`BOOK_REVIEW` |
| `status` | string | 否 | 默认 `DRAFT`；非空时必须是 `DRAFT` 或 `PUBLISHED` |
| `categoryIds` | long[] | 否 | 逐条插入关联；重复 ID 会触发数据库唯一键冲突 |
| `tagIds` | long[] | 否 | 逐条插入关联 |
| `metadata` | string | 否 | 作为字符串存入数据库 |

成功返回 HTTP 200 和 `ContentDetailVO`，不是 201。

如果 `type` 或 `status` 字符串不是代码枚举允许的值，`valueOf()` 会抛出 `IllegalArgumentException`，当前由全局兜底处理为 500，而不是 400。

### 4.4 编辑文章

```http
PUT /api/admin/contents/{id}
Authorization: Bearer <token>
```

请求体与新建相同。

实际行为：

- `slug` 或 `type` 导致路径变化时，先删除旧 Markdown，再写新文件。
- 每次编辑会先删除全部分类/标签关联，再按请求重新插入。
- 从非发布状态首次变为 `PUBLISHED` 时设置 `publishedAt`。
- 已发布文章改回 `DRAFT` 时保留原 `publishedAt`。
- 成功返回 HTTP 200。

### 4.5 删除文章

```http
DELETE /api/admin/contents/{id}
Authorization: Bearer <token>
```

行为：删除 Markdown 文件、分类关联、标签关联和内容记录。文件删除失败会被忽略，但数据库删除继续执行。

成功返回 `204 No Content`。

---

## 5. 管理端分类

### 5.1 分类树

```http
GET /api/admin/categories
GET /api/admin/categories?type=NOTE
Authorization: Bearer <token>
```

返回与公开分类树相同的结构。

### 5.2 分类详情

```http
GET /api/admin/categories/{id}
Authorization: Bearer <token>
```

返回 `Category` 实体：

```json
{
  "id": 1,
  "name": "编程",
  "slug": "programming",
  "parentId": null,
  "type": "NOTE",
  "sortOrder": 0,
  "createdAt": "2026-06-26T10:00:00",
  "updatedAt": "2026-06-26T10:00:00",
  "children": null
}
```

### 5.3 新建分类

```http
POST /api/admin/categories
Authorization: Bearer <token>
```

```json
{
  "name": "编程",
  "slug": "programming",
  "parentId": null,
  "type": "NOTE",
  "sortOrder": 0
}
```

`name`、`slug` 必填；`sortOrder` 为空时使用 0。`type` 当前没有 `@NotBlank` 或枚举校验。

成功返回 HTTP 200 和新建后的 `Category`。由于 Controller 没有重新查询数据库，该响应的 `createdAt`、`updatedAt` 通常为 `null`。

### 5.4 编辑分类

```http
PUT /api/admin/categories/{id}
Authorization: Bearer <token>
```

请求体同新建，成功返回更新后的 `Category` 对象。

### 5.5 删除分类

```http
DELETE /api/admin/categories/{id}
Authorization: Bearer <token>
```

代码意图是有关联内容时返回 409，但当前 `CategoryManageServiceImpl.delete` 的关联查询方向错误，删除保护不可靠。详见 `docs/project/audit-log.md`。

成功返回 `204 No Content`。

---

## 6. 管理端标签

### 6.1 标签列表

```http
GET /api/admin/tags
Authorization: Bearer <token>
```

返回 `Tag` 实体数组，包含 `id`、`name`、`slug`、`createdAt`。

### 6.2 新建标签

```http
POST /api/admin/tags
Authorization: Bearer <token>
```

```json
{
  "name": "Java",
  "slug": "java"
}
```

成功返回 HTTP 200 和新建对象；`createdAt` 通常为 `null`。

### 6.3 编辑标签

```http
PUT /api/admin/tags/{id}
Authorization: Bearer <token>
```

请求体同新建，成功返回更新后的 `Tag`。

### 6.4 删除标签

```http
DELETE /api/admin/tags/{id}
Authorization: Bearer <token>
```

代码意图是有关联内容时返回 409，但当前 `TagManageServiceImpl.delete` 同样存在关联查询方向错误，删除保护不可靠。

成功返回 `204 No Content`。

---

## 7. 图片与站点配置

### 7.1 上传图片

```http
POST /api/admin/images/upload
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `file` | file | MIME 必须是 jpg/png/gif/webp |

响应：

```json
{
  "id": 1,
  "url": "/images/2026/09/uuid.png",
  "originalName": "screenshot.png",
  "size": 204800
}
```

实际限制由 Spring Multipart 配置提供：单文件 50MB，单请求 50MB。

### 7.2 获取全部站点配置

```http
GET /api/admin/options
Authorization: Bearer <token>
```

```json
{
  "site_title": "Umo Blog",
  "site_subtitle": "代码 · 阅读 · 创作",
  "about_page": "## 关于我\n...",
  "project_page": "## 项目\n..."
}
```

### 7.3 更新站点配置

```http
PUT /api/admin/options/{key}
Authorization: Bearer <token>
```

```json
{
  "value": "新的值"
}
```

`value` 必填。底层使用 MySQL `ON DUPLICATE KEY UPDATE`，可新增未存在的 key。

成功返回 `204 No Content`。
