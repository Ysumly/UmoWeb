# 管理端 API 实现

> 基线日期: 2026-09-10
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

1. `UserMapper.findByUsername(username)`。
2. 用户不存在或 BCrypt 不匹配时抛 `UnauthorizedException`。
3. 签发 JWT。
4. 返回 `token` 和 `expiresAt`。

成功状态为 200。

### 2.2 修改密码

```java
@PutMapping("/change-password")
ResponseEntity<Void>
```

用户名来自拦截器写入的 `adminUsername` request attribute。

旧密码错误返回 401，新密码少于 6 位返回 400，成功返回 204。

---

## 3. 文章管理

### 3.1 列表

```http
GET /api/admin/contents
```

使用与公开列表相同的 `ContentQuery`，但通过 `findAll` 查询。

支持：

- `page`
- `size`
- `type`
- `categoryId`
- `tagId`
- `status`
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

1. 将 `type` 转为 `ContentType`。
2. 将 `status` 转为 `ContentStatus`，为空时默认 `DRAFT`。
3. 生成 `body_path`。
4. 同步写入 Markdown。
5. 插入 `contents`。
6. 插入分类和标签关联。
7. 返回详情 VO。

发布状态下会设置 `publishedAt = LocalDateTime.now()`。

方法有 `@Transactional`，但文件写入不会被数据库事务回滚。

成功状态是 200，不是 201。

### 3.4 编辑

```http
PUT /api/admin/contents/{id}
```

流程：

1. 查询旧记录，不存在则 404。
2. 计算新路径。
3. 路径变化时删除旧 Markdown，再写新文件。
4. 更新数据库字段。
5. 首次发布时设置 `publishedAt`。
6. 删除旧分类/标签关联并重建。

风险：

- 删除旧文件先于写新文件。
- 文件系统失败无法由数据库事务回滚。

### 3.5 删除

```http
DELETE /api/admin/contents/{id}
```

删除 Markdown、分类关联、标签关联和内容记录。Markdown 删除失败会被忽略。

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

直接返回 `Category` 实体，包括非数据库字段 `children`。

### 4.3 新建与编辑

```http
POST /api/admin/categories
PUT /api/admin/categories/{id}
```

请求 DTO：

```java
String name;       // @NotBlank
String slug;       // @NotBlank
Long parentId;
String type;       // 当前没有必填或枚举校验
Integer sortOrder; // 空时默认 0
```

新建后直接返回内存中的 Category；时间字段通常为 `null`。

### 4.4 删除

```http
DELETE /api/admin/categories/{id}
```

设计目标是有关联内容时返回 409。

当前实现错误地执行：

```java
contentCategoryMapper.findCategoryIdsByContentId(id)
```

这里的 `id` 是分类 ID，却被当成内容 ID 查询。该保护不可靠。

成功删除返回 204。

---

## 5. 标签管理

### 5.1 列表

```http
GET /api/admin/tags
```

返回 `Tag` 实体列表，而不是公开端的 `TagVO`。

### 5.2 新建与编辑

```http
POST /api/admin/tags
PUT /api/admin/tags/{id}
```

`name` 和 `slug` 必填。

### 5.3 删除

```http
DELETE /api/admin/tags/{id}
```

设计目标同样是有关联内容时返回 409。

当前实现错误地调用：

```java
contentTagMapper.findTagIdsByContentId(id)
```

传入的是标签 ID，而不是内容 ID，因此删除保护不可靠。

成功返回 204。

---

## 6. 图片上传

```http
POST /api/admin/images/upload
Content-Type: multipart/form-data
```

流程：

1. 根据 MIME 白名单校验文件类型。
2. 生成 `images/{YYYY}/{MM}/{uuid}.{ext}`。
3. 写磁盘。
4. 插入 `images`。
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

测试使用 Mock Service，不能验证真实 SQL 或真实文件操作。
