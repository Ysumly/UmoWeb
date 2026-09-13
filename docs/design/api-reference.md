# UmoWeb API 接口参考

> 基线日期: 2026-09-13
> 事实来源: `controller/`、`model/dto/`、`model/vo/`、`GlobalExceptionHandler`、Mapper XML
> 接口总数: 公开端 8 个，管理端 19 个，共 27 个
> 实测状态: 2026-09-11 在隔离 MySQL 5.7 迁移副本上完成 27/27 接口冒烟，接口契约未变更

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
| 400 | 请求体缺失、参数校验失败、非法枚举/JSON、图片类型或文件签名不支持 |
| 401 | 未认证、JWT 无效或过期、用户名密码错误 |
| 404 | 内容、分类、标签不存在 |
| 409 | 唯一键冲突、关联内容/子分类删除保护、数据关联冲突 |
| 413 | Multipart 文件或请求超过 50MB |
| 429 | 搜索同 IP 10 秒内重复请求，或同一用户名/IP 连续登录失败过多 |
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
GET /api/public/contents?page=1&size=10&type=NOTE&categoryId=2&includeDescendants=true&tagId=1&sort=published_at_desc
```

| 参数 | 类型 | 默认值 | 实际行为 |
|---|---|---|---|
| `page` | int | 1 | 1-1000000 |
| `size` | int | 10 | 1 到 100 |
| `type` | string | - | `NOTE`、`NOVEL`、`BOOK_REVIEW`，非法值返回 400 |
| `categoryId` | long | - | 默认仅匹配该分类；与 `includeDescendants=true` 联用时包含全部后代 |
| `includeDescendants` | boolean | `false` | 仅在传入 `categoryId` 时有效；缺少 `categoryId` 返回 400 |
| `tagId` | long | - | 精确匹配标签 |
| `sort` | string | `published_at_desc` | 仅严格等于 `created_at_desc` 时按创建时间倒序，其他值按发布时间倒序 |

仅返回 `status=PUBLISHED`。后代筛选使用解析后的分类 ID 集合和 `EXISTS`，内容不会因同时关联
父级与子级而重复；相同排序时间按 `id DESC` 稳定排序。

分类层级在请求命中范围内存在循环或超过 32 层时返回 409，错误消息分别为
“分类层级包含循环”和“分类层级超过 32 层”。选择不存在的分类仍返回 200 和空结果。

```json
{
  "items": [
    {
      "id": 1,
      "title": "Spring Boot 快速上手",
      "slug": "spring-boot-quickstart",
      "summary": "从零搭建一个 Spring Boot 项目。",
      "type": "NOTE",
      "status": "PUBLISHED",
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

`metadata` 数据库中为 JSON 字符串，写入时必须是非空时的合法 JSON 对象；列表 VO 中会解析为对象。
分类和标签关联通过 `contentIds` 批量查询，列表查询次数不随文章数线性增长。

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
  "status": "PUBLISHED",
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
  "metadata": {},
  "publishedAt": "2026-06-20T10:00:00",
  "previous": {
    "id": 1,
    "title": "更早的文章",
    "slug": "older-article",
    "publishedAt": "2026-06-19T10:00:00"
  },
  "next": {
    "id": 3,
    "title": "更新的文章",
    "slug": "newer-article",
    "publishedAt": "2026-06-21T10:00:00"
  },
  "body": "# Spring Boot\n\nMarkdown 原文..."
}
```

行为：

- 只查询 `PUBLISHED` 内容。
- `slug` 不存在或内容不是已发布状态时返回 404。
- 详情和列表使用同一个共享 VO 组装组件，`categories`、`tags` 始终为数组。
- `previous` 表示按发布时间更早的已发布文章，`next` 表示更晚的文章；对象仅含 `id`、`title`、`slug`、`publishedAt`，边界位置为 `null`。
- 发布时间相同时，较小 ID 视为更早、较大 ID 视为更晚。
- Markdown 文件读取失败时返回 200，`body` 为 `""`。

### 2.8 搜索已发布文章

```http
GET /api/public/contents/search?q=Spring&page=1&size=10
```

| 参数 | 类型 | 默认值 | 实际行为 |
|---|---|---|---|
| `q` | string | `""` | 匹配 `title LIKE` 或 `summary LIKE` |
| `page` | int | 1 | 1-1000000 |
| `size` | int | 10 | 1 到 100 |

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

此接口不经过 `AdminInterceptor`，但同一用户名/IP 在 15 分钟内连续失败 5 次后会返回 429。
客户端 `X-Forwarded-For` 仅在请求直接来自 `app.security.trusted-proxies` 配置的精确 IP 或 CIDR 代理时生效。

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

约束：`newPassword` 至少 6 位。修改成功后数据库 `token_version` 递增，旧 JWT 立即失效。

成功返回 `204 No Content`。

---

## 4. 管理端文章

### 4.1 查询文章列表

```http
GET /api/admin/contents?page=1&size=10&type=NOTE&status=DRAFT&categoryId=2&includeDescendants=true&tagId=1&sort=created_at_desc
Authorization: Bearer <token>
```

参数与公开列表一致，额外支持：

| 参数 | 类型 | 说明 |
|---|---|---|
| `status` | string | `DRAFT` 或 `PUBLISHED`；不传返回全部 |

`includeDescendants` 同样适用于管理端列表。返回含草稿的分页 `PageResult<ContentListVO>`。

`ContentListVO` 包含 `status` 字段：公开接口只会返回 `PUBLISHED`；管理端列表和详情会返回 `DRAFT` 或 `PUBLISHED`。

### 4.2 查询文章详情

```http
GET /api/admin/contents/{id}
Authorization: Bearer <token>
```

返回 `ContentDetailVO`，含 Markdown `body` 和当前 `status`。资源不存在返回 404。

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
| `slug` | string | 是 | 1-200 位安全字符，仅字母、数字、`_`、`-`；数据库唯一 |
| `body` | string | 否 | `null` 时写入空字符串 |
| `summary` | string | 否 | 最长 2000 |
| `type` | string | 是 | 必须为 `NOTE`、`NOVEL`、`BOOK_REVIEW`，非法值返回 400 |
| `status` | string | 否 | 默认 `DRAFT`；非空时必须是 `DRAFT` 或 `PUBLISHED` |
| `categoryIds` | long[] | 否 | 必须全部存在；重复 ID 会去重 |
| `tagIds` | long[] | 否 | 必须全部存在；重复 ID 会去重 |
| `metadata` | string | 否 | 非空时必须是合法 JSON 对象 |

成功返回 HTTP 200 和 `ContentDetailVO`，不是 201。

`NOVEL` 还必须关联至少一个 `type=NOVEL` 的分类，第一条关联分类的 slug 用作 `bookSlug`。

文件一致性：

1. 先拒绝重复 slug 和已存在的最终 Markdown 文件。
2. 写入同目录临时文件。
3. 插入数据库和关联。
4. 数据库成功后以“不覆盖”方式移动到最终路径。
5. 数据库回滚时清理已提升文件；已有旧文件不会被覆盖。

### 4.4 编辑文章

```http
PUT /api/admin/contents/{id}
Authorization: Bearer <token>
```

请求体与新建相同。

实际行为：

- 新内容先写同目录临时文件，数据库更新成功后再原子替换最终 Markdown。
- 路径变化时旧文件在事务成功提交后才清理。
- 数据库回滚时，同路径更新会恢复旧文件内容；路径变化会删除新文件并保留旧文件。
- 每次编辑会先删除全部分类/标签关联，再按请求重新插入。
- 从非发布状态首次变为 `PUBLISHED` 时设置 `publishedAt`。
- 已发布文章改回 `DRAFT` 时保留原 `publishedAt`。
- 成功返回 HTTP 200。

### 4.5 删除文章

```http
DELETE /api/admin/contents/{id}
Authorization: Bearer <token>
```

行为：在数据库事务中删除分类关联、标签关联和内容记录；提交成功后才删除 Markdown。
数据库失败时文件和旧数据保留；提交后文件删除失败时记录错误日志并留下可清理的孤儿文件。

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

返回管理端 `CategoryVO`：

```json
{
  "id": 1,
  "name": "编程",
  "slug": "programming",
  "parentId": null,
  "type": "NOTE",
  "sortOrder": 0
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

`name`、`slug`、`type` 必填；`slug` 使用安全字符和长度限制；`type` 必须是 `NOTE`、`NOVEL`、`BOOK_REVIEW`；`sortOrder` 为空时使用 0。

成功返回 HTTP 200 和新建后的 `CategoryVO`，不直接返回数据库 Entity。

### 5.4 编辑分类

```http
PUT /api/admin/categories/{id}
Authorization: Bearer <token>
```

请求体同新建，成功返回更新后的 `CategoryVO`。

### 5.5 删除分类

```http
DELETE /api/admin/categories/{id}
Authorization: Bearer <token>
```

按 `category_id` 统计关联内容；有关联内容时返回 409。仍有子分类时也返回 409，当前不自动重挂接。

成功返回 `204 No Content`。

---

## 6. 管理端标签

### 6.1 标签列表

```http
GET /api/admin/tags
Authorization: Bearer <token>
```

返回 `TagVO` 数组，仅包含 `id`、`name`、`slug`。

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

成功返回 HTTP 200 和 `TagVO`。

### 6.3 编辑标签

```http
PUT /api/admin/tags/{id}
Authorization: Bearer <token>
```

请求体同新建，成功返回更新后的 `TagVO`。

### 6.4 删除标签

```http
DELETE /api/admin/tags/{id}
Authorization: Bearer <token>
```

按 `tag_id` 统计关联内容；有关联内容时返回 409。

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
| `file` | file | MIME 必须是 jpg/png/gif/webp，且文件签名必须匹配 |

响应：

```json
{
  "id": 1,
  "url": "/images/2026/09/uuid.png",
  "originalName": "screenshot.png",
  "size": 204800
}
```

实际限制由 Spring Multipart 配置提供：单文件 50MB，单请求 50MB。扩展名只由 MIME 映射生成，
不采用客户端原始扩展名；`originalFilename` 为 null/空或包含路径时会被安全化，仅作为展示元信息保存。

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
