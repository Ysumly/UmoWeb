# UmoWeb API 接口参考

> 基线日期: 2026-09-18
> 事实来源: `controller/`、`model/dto/`、`model/vo/`、`GlobalExceptionHandler`、Mapper XML
> 接口总数: 公开端 8 个，管理端 29 个，共 37 个
> 实测状态: 2026-09-18 新增 6 个 AI 模式目录接口；既有 31/31 冒烟继续作为兼容基线，
> 六个新接口由 Controller 测试和 MySQL 门控 Mapper 测试覆盖，完整 AI HTTP 冒烟由 5.1F 扩展

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
| 404 | 内容、分类、标签或 AI 模式/版本不存在 |
| 409 | 唯一键冲突、关联内容/子分类删除保护、AI 模式版本冲突或数据关联冲突 |
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
  "related": [
    {
      "id": 4,
      "title": "相关文章",
      "slug": "related-article",
      "summary": "与当前文章共享标签或分类。",
      "type": "NOTE",
      "status": "PUBLISHED",
      "categories": [],
      "tags": [],
      "metadata": {},
      "publishedAt": "2026-06-18T10:00:00"
    }
  ],
  "body": "# Spring Boot\n\nMarkdown 原文..."
}
```

行为：

- 只查询 `PUBLISHED` 内容。
- `slug` 不存在或内容不是已发布状态时返回 404。
- 详情和列表使用同一个共享 VO 组装组件，`categories`、`tags` 始终为数组。
- `previous` 表示按发布时间更早的已发布文章，`next` 表示更晚的文章；对象仅含 `id`、`title`、`slug`、`publishedAt`，边界位置为 `null`。
- 发布时间相同时，较小 ID 视为更早、较大 ID 视为更晚。
- `related` 始终为数组，最多 4 篇；只返回 `PUBLISHED` 内容，并排除当前、`previous` 和 `next`。
- 相关排序为共享标签数 × 3、共享分类数 × 2、同类型 1 分，再按 `publishedAt DESC, id DESC`；
  分类按精确 ID 交集计算，不展开祖先或后代。查询失败时返回空数组，不影响正文和前后导航。
- `related` 项结构等同列表项；管理端详情不包含该字段。
- Markdown 文件读取失败时返回 200，`body` 为 `""`。

### 2.8 搜索已发布文章

```http
GET /api/public/contents/search?q=Spring&page=1&size=10
```

| 参数 | 类型 | 默认值 | 实际行为 |
|---|---|---|---|
| `q` | string | `""` | 正文全文匹配，或 `title` / `summary` 子串匹配 |
| `page` | int | 1 | 1-1000000 |
| `size` | int | 10 | 1 到 100 |

说明：

- Markdown 正文使用 MySQL 8.4 `FULLTEXT ... WITH PARSER ngram` 索引。
- 无 `q` 时返回全部已发布内容，不生成 `excerpt`。
- 正文命中时列表项包含可选 `excerpt`；否则回退摘要或正文开头。
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

## 8. 管理端 AI 模式目录

六个接口均要求 `Authorization: Bearer <token>`。模式写入成功统一返回 HTTP 200；
`modeKey` 格式为 `[A-Z][A-Z0-9_]{2,63}`，创建后不可修改。

### 8.1 查询模式

```http
GET /api/admin/ai/modes
Authorization: Bearer <token>
```

返回 `AiModeSettingsVO` 数组，按 `sortOrder ASC, id ASC` 排序。每项包含
`id`、`modeKey`、`name`、`description`、`enabled`、`sortOrder`、`currentVersion`、
`systemPrompt`、`validationProfile`、`createdAt` 和 `updatedAt`。

### 8.2 新建模式

```http
POST /api/admin/ai/modes
Authorization: Bearer <token>
```

```json
{
  "modeKey": "CUSTOM_MODE",
  "name": "自定义模式",
  "description": "说明",
  "systemPrompt": "系统提示词",
  "validationProfile": "NONE",
  "enabled": false,
  "sortOrder": 0
}
```

模式在创建时始终写入 version 1；`enabled` 和 `sortOrder` 未传时分别为 `false` 和 `0`。
重复 `modeKey` 返回 409。

### 8.3 复制模式

```http
POST /api/admin/ai/modes/{id}/copy
Authorization: Bearer <token>
```

```json
{
  "modeKey": "COPIED_MODE",
  "name": "复制模式"
}
```

复制当前版本的说明、系统提示词和校验策略，新模式从 version 1 开始且保持停用。
源模式不存在返回 404，重复 `modeKey` 返回 409。

### 8.4 更新模式

```http
PUT /api/admin/ai/modes/{id}
Authorization: Bearer <token>
```

```json
{
  "name": "自定义模式",
  "description": "说明",
  "systemPrompt": "更新后的系统提示词",
  "validationProfile": "NONE",
  "enabled": true,
  "sortOrder": 1,
  "expectedVersion": 1
}
```

只修改名称、说明、启停或排序时不生成新版本；系统提示词或校验策略变化时生成
`expectedVersion + 1`，并只保留最近 10 版。`expectedVersion` 与当前版本不一致返回 409。

### 8.5 查询历史版本

```http
GET /api/admin/ai/modes/{id}/versions
Authorization: Bearer <token>
```

返回按 `versionNo DESC` 排序的版本数组，每项包含 `versionNo`、`systemPrompt`、
`validationProfile` 和 `createdAt`。模式不存在返回 404。

### 8.6 回滚提示词

```http
POST /api/admin/ai/modes/{id}/rollback/{versionNo}
Authorization: Bearer <token>
```

```json
{
  "expectedVersion": 3
}
```

回滚会复制目标历史内容生成 `expectedVersion + 1`，不改写历史记录，并返回新的
`AiModeSettingsVO`。模式或版本不存在返回 404，版本冲突返回 409。

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
| `status` | string | `DRAFT`、`SCHEDULED`、`PUBLISHED` 或 `ARCHIVED`；不传返回全部 |

`includeDescendants` 同样适用于管理端列表。返回含草稿的分页 `PageResult<ContentListVO>`。

`ContentListVO` 包含 `status` 与可选 `scheduledAt`：公开接口只会返回 `PUBLISHED`；
管理端列表和详情可返回 `DRAFT`、`SCHEDULED`、`PUBLISHED` 或 `ARCHIVED`。

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
| `scheduledAt` | string | 否 | `status=SCHEDULED` 时必填，必须是晚于当前时间的 ISO 本地时间 |
| `status` | string | 否 | 默认 `DRAFT`；允许 `DRAFT`、`SCHEDULED`、`PUBLISHED`、`ARCHIVED`；新建不能直接归档 |
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
- 仅 `publishedAt` 为空的草稿可设为 `SCHEDULED`；计划时间到点后由服务器发布。
- 从非发布状态手动变为 `PUBLISHED` 时设置当前 `publishedAt`；调度发布使用原计划时间。
- 已发布文章改回 `DRAFT` 时保留原 `publishedAt`。
- `SCHEDULED` 改期必须提交新的未来时间；取消回 `DRAFT`、归档或提前发布都会清空 `scheduledAt`。
- 成功返回 HTTP 200。

### 4.5 删除文章

```http
DELETE /api/admin/contents/{id}
Authorization: Bearer <token>
```

行为：在数据库事务中删除分类关联、标签关联和内容记录；提交成功后才删除 Markdown。
数据库失败时文件和旧数据保留；提交后文件删除失败时记录错误日志并留下可清理的孤儿文件。

成功返回 `204 No Content`。

### 4.6 批量更新文章

```http
POST /api/admin/contents/bulk
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "action": "ADD_TAGS",
  "contentIds": [1, 2],
  "tagIds": [3]
}
```

`action` 支持：

| action | 行为 | 必填目标 |
|---|---|---|
| `ADD_CATEGORIES` | 保留现有关联并添加分类 | `categoryIds` |
| `REMOVE_CATEGORIES` | 移除指定分类 | `categoryIds` |
| `ADD_TAGS` | 保留现有关联并添加标签 | `tagIds` |
| `REMOVE_TAGS` | 移除指定标签 | `tagIds` |
| `ARCHIVE` | 状态改为 `ARCHIVED`，清空计划时间 | - |
| `RESTORE_DRAFT` | 已归档内容恢复为 `DRAFT`，保留 `publishedAt` | - |

`contentIds` 必须为 1-100 个唯一存在的内容；分类或标签批量操作的目标 ID 也限制为
最多 100 个且必须存在。整批操作先预检再在单个事务中提交，任一内容无效时不产生部分更新。
小说内容的分类移除需要在编辑页处理，因为 `body_path` 依赖小说目录。

成功响应：

```json
{
  "action": "ADD_TAGS",
  "requestedCount": 2,
  "updatedCount": 2,
  "unchangedCount": 0
}
```

预检失败时返回对应 400/404/409，并保留通用 `code`、`message`，额外返回逐项 `failures`：

```json
{
  "code": 409,
  "message": "只有已归档内容可以恢复为草稿",
  "failures": [
    {
      "contentId": 7,
      "targetId": null,
      "reason": "NOT_ARCHIVED"
    }
  ]
}
```

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

### 7.2 查询图片

```http
GET /api/admin/images?page=1&size=24&usage=ORPHANED
Authorization: Bearer <token>
```

`usage` 可省略或使用 `REFERENCED`、`ORPHANED`。响应按创建时间和 ID 倒序：

```json
{
  "items": [
    {
      "id": 1,
      "url": "/images/2026/09/uuid.png",
      "originalName": "screenshot.png",
      "size": 204800,
      "contentType": "image/png",
      "createdAt": "2026-09-13T10:00:00",
      "referenced": false
    }
  ],
  "page": 1,
  "size": 24,
  "total": 1
}
```

引用状态扫描全部文章状态（`DRAFT`、`SCHEDULED`、`PUBLISHED`、`ARCHIVED`）的 Markdown，
以及 `about_page`、`project_page`；
只识别规范 `/images/...` 路径。Markdown 文件缺失只记录警告，不影响列表。

### 7.3 检查图片一致性

```http
GET /api/admin/images/integrity
Authorization: Bearer <token>
```

只读扫描全部文章 Markdown 与 About/Project，并对 `images` 目录做数据库和文件系统一致性检查。
存在异常时仍返回 200：

```json
{
  "scannedAt": "2026-09-15T21:00:00",
  "counts": {
    "brokenReferences": 2,
    "missingFiles": 1,
    "untrackedFiles": 1,
    "total": 4
  },
  "brokenReferences": [
    {
      "url": "/images/2026/09/missing-record.png",
      "sourceType": "CONTENT",
      "sourceId": 12,
      "sourceLabel": "文章标题"
    }
  ],
  "missingFiles": [
    {
      "id": 9,
      "url": "/images/2026/09/missing-file.png",
      "originalName": "missing-file.png"
    }
  ],
  "untrackedFiles": [
    { "url": "/images/2026/09/untracked-file.png" }
  ]
}
```

`brokenReferences` 表示正文或固定页引用了 `/images/...`，但 `images` 表无对应路径；
`missingFiles` 表示数据库记录存在但磁盘普通文件缺失；`untrackedFiles` 表示磁盘存在普通文件、
数据库无记录且未被内容引用。已有数据库记录但未被引用仍属于列表中的 `ORPHANED`，不算一致性问题。
扫描无法可靠完成时返回 500；不自动修复、不持久化历史。

### 7.4 删除图片

```http
DELETE /api/admin/images/{id}
Authorization: Bearer <token>
```

图片仍被文章或固定页引用时返回 409，不存在返回 404。未引用图片删除成功返回
`204 No Content`。

删除事务同时移除 `images` 记录并写入 `image_cleanup_queue`。事务提交后删除文件；
文件删除失败时保留队列、记录次数和错误，并在下次应用启动或图片操作前重试。

### 7.5 获取全部站点配置

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

### 7.6 更新站点配置

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
