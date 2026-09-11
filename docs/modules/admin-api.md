# 管理端 API 实现

> 基线日期: 2026-09-11
> 前缀: `/api/admin`
> 接口数: 19，其中登录无需 JWT

---

## 1. Controller

| Controller | 接口 |
|---|---|
| `AuthController` | 登录、修改密码 |
| `ContentManageController` | 文章列表、详情、新建、编辑、删除 |
| `CategoryManageController` | 分类树、详情、新建、编辑、删除 |
| `OptionController` | 配置查询、配置更新 |
| `TagManageController` | 标签列表、新建、编辑、删除 |
| `ImageController` | 图片上传 |

除 `/api/admin/login` 外，所有接口都由 `AdminInterceptor` 检查 JWT。

---

## 2. 认证

### 2.1 登录

```java
@PostMapping("/login")
ResponseEntity<Map<String, Object>>
```

流程：

1. 按 username + 解析后的客户端 IP 检查登录失败窗口。
2. `UserMapper.findByUsername(username)`。
3. 用户不存在或 BCrypt 不匹配时记录失败并抛 `UnauthorizedException`；达到 5 次后返回 429。
4. 成功后清除失败记录，签发包含 `tokenVersion` 的 JWT。
5. 返回 `token` 和 `expiresAt`。

成功状态为 200。

### 2.2 修改密码

```java
@PutMapping("/change-password")
ResponseEntity<Void>
```

用户名来自拦截器写入的 `adminUsername` request attribute。更新密码时 SQL 原子递增 `token_version`，
因此修改前签发的旧 token 会在下一次请求时失效。

旧密码错误返回 401，新密码少于 6 位返回 400，成功返回 204。

---

## 3. 文章管理

### 3.1 列表

```http
GET /api/admin/contents
```

使用与公开列表相同的 `ContentQuery`，但通过 `findAll` 查询。

支持：

- `page`（1-1000000）
- `size`（1-100）
- `type`（枚举）
- `categoryId`
- `tagId`
- `status`（枚举）
- `sort`

`status` 为空时包含草稿和已发布内容。

### 3.2 详情

```http
GET /api/admin/contents/{id}
```

返回 `ContentDetailVO`。内容不存在返回 404。

### 3.3 新建

```http
POST /api/admin/contents
```

流程：

1. 校验 DTO 枚举、metadata JSON、分类/标签存在性和 slug 安全字符。
2. 检查数据库 slug 和最终 Markdown 文件均不存在。
3. 写同目录临时 Markdown，插入 `contents` 和关联。
4. 数据库操作成功后以不覆盖方式提升到最终路径。
5. 返回详情 VO；任何数据库回滚都会清理新文件。

发布状态下会设置 `publishedAt = LocalDateTime.now()`。

现有文件不会被覆盖，数据库失败不会破坏旧文件。

成功状态是 200，不是 201。

### 3.4 编辑

```http
PUT /api/admin/contents/{id}
```

流程：

1. 查询旧记录，不存在则 404。
2. 校验 slug、枚举、metadata 和关联 ID，检查新路径是否被其他文件占用。
3. 同路径更新先备份旧文件；写新临时文件。
4. 更新数据库字段，首次发布时设置 `publishedAt`，删除并重建分类/标签关联。
5. 数据库成功后原子替换最终文件；路径变化时提交后删除旧文件。
6. 数据库回滚时恢复同路径旧内容，或删除新路径并保留旧文件。

### 3.5 删除

```http
DELETE /api/admin/contents/{id}
```

事务内删除分类关联、标签关联和内容记录；提交成功后才删除 Markdown。
数据库失败时文件保留；提交后文件删除失败会记录日志并留下可恢复孤儿文件。

成功状态为 204。

---

## 4. 分类管理

### 4.1 分类树

```http
GET /api/admin/categories?type=NOTE
```

使用与公开端相同的树形 VO 组装逻辑。

### 4.2 分类详情

```http
GET /api/admin/categories/{id}
```

返回 `CategoryVO`，字段为 `id`、`name`、`slug`、`parentId`、`type`、`sortOrder`，
不直接返回数据库 Entity。

### 4.3 新建与编辑

```http
POST /api/admin/categories
PUT /api/admin/categories/{id}
```

请求 DTO：

```java
String name;       // @NotBlank, max 100
String slug;       // @NotBlank, safe chars, max 100
Long parentId;
String type;       // @NotBlank, NOTE/NOVEL/BOOK_REVIEW
Integer sortOrder; // 空时默认 0
```

新建/编辑返回 `CategoryVO`，不直接返回 Entity。

### 4.4 删除

```http
DELETE /api/admin/categories/{id}
```

按 `category_id` 统计内容关联：

```java
contentCategoryMapper.countContentsByCategoryId(id)
```

有关联内容返回 409。`categoryMapper.countChildren(id)` 大于 0 时同样返回 409，
不会删除父分类或隐式重挂接子分类。

成功删除返回 204。

---

## 5. 标签管理

### 5.1 列表

```http
GET /api/admin/tags
```

返回 `TagVO` 列表，不直接返回 Entity。

### 5.2 新建与编辑

```http
POST /api/admin/tags
PUT /api/admin/tags/{id}
```

`name` 和 `slug` 必填，均有长度限制；`slug` 只允许安全字符。

### 5.3 删除

```http
DELETE /api/admin/tags/{id}
```

按 `tag_id` 统计内容关联：

```java
contentTagMapper.countContentsByTagId(id)
```

有关联内容返回 409。

成功返回 204。

---

## 6. 图片上传

```http
POST /api/admin/images/upload
Content-Type: multipart/form-data
```

流程：

1. 根据 MIME 白名单和文件头签名共同校验类型。
2. 由 MIME 映射固定扩展名，生成 `images/{YYYY}/{MM}/{uuid}.{ext}`。
3. 安全化原始文件名并写磁盘。
4. 插入 `images`；数据库失败时删除刚写入的文件。
5. 返回 URL 和元信息。

允许：

```text
image/jpeg
image/png
image/gif
image/webp
```

最大 50MB，由 Spring Multipart 配置限制。

---

## 7. 站点配置

### 7.1 查询

```http
GET /api/admin/options
```

返回 `Map<String, String>`。

### 7.2 更新

```http
PUT /api/admin/options/{key}
```

请求：

```json
{
  "value": "新的值"
}
```

底层使用 upsert，因此可以创建新的 key。成功返回 204。

---

## 8. 测试现状

`BoundaryTest` 仍使用 Mock Service 覆盖接口边界，另有 Service/Util 单元测试覆盖真实文件、
路径、JWT、限流、容器装配和批量查询行为。当前完整后端测试共 77 个。

`BoundaryTest` 覆盖：

- 登录缺少 body 返回 400。
- 空用户名密码返回 400。
- 密码错误返回 401。
- 新建分类空字段返回 400。
- 文章 ID 不存在返回 404。
- 更新配置空值返回 400。
- 新建文章必填字段为空返回 400。
- 修改密码过短返回 400。
- 新建标签空字段返回 400。
- 有关联分类删除返回 409 的 Service 异常路径。
- 非法 page/size/type/metadata 返回 400。
- 公开详情返回 categories/tags 数组。

文件、路径、JWT、上传和 VO 组装测试使用真实临时文件或真实工具类；完整 SQL 仍需自动化 MySQL 集成测试。
2026-09-11 已通过真实 MySQL 副本冒烟验证管理端全部接口，脚本见 `docs/project/testing-guide.md`。
