# Data 层实现

> 基线日期: 2026-09-13
> 路径: `model/`、`mapper/`、`resources/mapper/`

---

## 1. 模型数量

| 类别 | 数量 |
|---|---:|
| Entity | 6 |
| DTO | 10 |
| VO | 8 |
| Mapper 接口 | 8 |
| Mapper XML | 8 |

---

## 2. Entity

### 2.1 User

```java
Long id;
String username;
String passwordHash;
Integer tokenVersion;
LocalDateTime createdAt;
LocalDateTime updatedAt;
```

对应 `users`。

### 2.2 Category

```java
Long id;
String name;
String slug;
Long parentId;
String type;
Integer sortOrder;
LocalDateTime createdAt;
LocalDateTime updatedAt;
List<Category> children;
```

`children` 不是数据库字段，用于树形组装。

### 2.3 Tag

```java
Long id;
String name;
String slug;
LocalDateTime createdAt;
```

### 2.4 Content

```java
Long id;
String title;
String slug;
String bodyPath;
String summary;
String type;
String status;
String metadata;
LocalDateTime createdAt;
LocalDateTime updatedAt;
LocalDateTime publishedAt;
```

`type` 和 `status` 在 Entity 中仍是字符串。

### 2.5 Image

```java
Long id;
String originalName;
String storedName;
String path;
Long size;
String contentType;
Integer width;
Integer height;
LocalDateTime createdAt;
```

当前上传不解析宽高，因此 `width`、`height` 始终为 `null`。

### 2.6 SiteOption

```java
Long id;
String optionKey;
String optionValue;
LocalDateTime createdAt;
LocalDateTime updatedAt;
```

---

## 3. DTO

| DTO | 字段 | 校验 |
|---|---|---|
| `LoginRequest` | `username`、`password` | 两者 `@NotBlank` |
| `ChangePasswordRequest` | `oldPassword`、`newPassword` | 均必填，新密码至少 6 位 |
| `ContentSaveRequest` | `title`、`slug`、`body`、`summary`、`type`、`status`、`categoryIds`、`tagIds`、`metadata` | 必填、长度、安全 slug、枚举和 JSON 对象校验 |
| `ContentQuery` | `page`、`size`、`type`、`categoryId`、`includeDescendants`、`tagId`、`status`、`sort`、`q` | page >= 1，size 1-100，type/status 枚举，q <= 200，后代筛选依赖 categoryId |
| `CategorySaveRequest` | `name`、`slug`、`parentId`、`type`、`sortOrder` | 必填、长度、安全 slug、类型枚举 |
| `TagSaveRequest` | `name`、`slug` | 必填、长度、安全 slug |
| `OptionSaveRequest` | `value` | 必填 |
| `PageResult<T>` | `items`、`page`、`size`、`total` | 分页包装 |

`ContentQuery.getOffset()` 在合法输入下计算：

```java
(page - 1) * size
```

Controller 的 `@Valid` 会在进入 Service 前拒绝非法 page/size。

`ContentCategoryLink`、`ContentTagLink` 是批量关联查询的轻量 DTO，分别携带
`contentId/categoryId` 和 `contentId/tagId`。

---

## 4. VO

| VO | 字段 |
|---|---|
| `ContentListVO` | `id`、`title`、`slug`、`summary`、`type`、`status`、`categories`、`tags`、`metadata`、`publishedAt` |
| `ContentDetailVO` | 继承列表 VO，增加 `body`、`previous`、`next` |
| `ContentNeighborVO` | `id`、`title`、`slug`、`publishedAt` |
| `CategoryTreeVO` | `id`、`name`、`slug`、`type`、`children` |
| `CategoryVO` | `id`、`name`、`slug`、`parentId`、`type`、`sortOrder` |
| `TagVO` | `id`、`name`、`slug` |
| `ImageVO` | `id`、`url`、`originalName`、`size` |
| `SiteInfoVO` | `siteTitle`、`siteSubtitle`、`aboutHtml`、`projectHtml` |

`metadata` 是 `Map<String, Object>`；数据库字符串解析失败时返回空 Map。

---

## 5. Mapper

### 5.1 UserMapper

```java
User findByUsername(String username);
void insert(User user);
void updatePassword(String username, String passwordHash);
long count();
```

### 5.2 CategoryMapper

```java
List<Category> findAll();
List<Category> findByType(String type);
Category findById(Long id);
void insert(Category category);
void update(Category category);
void delete(Long id);
List<Category> findByIds(List<Long> ids);
long countChildren(Long parentId);
```

### 5.3 TagMapper

```java
List<Tag> findAll();
Tag findById(Long id);
void insert(Tag tag);
void update(Tag tag);
void delete(Long id);
List<Tag> findByIds(List<Long> ids);
List<Tag> findByContentId(Long contentId);
```

### 5.4 ContentMapper

```java
List<Content> findPublished(ContentQuery query, List<Long> categoryIds);
long countPublished(ContentQuery query, List<Long> categoryIds);
Content findBySlug(String slug);
Content findPreviousPublished(LocalDateTime publishedAt, Long id);
Content findNextPublished(LocalDateTime publishedAt, Long id);
List<Content> findAll(ContentQuery query, List<Long> categoryIds);
long countAll(ContentQuery query, List<Long> categoryIds);
Content findById(Long id);
long countBySlug(String slug, Long excludeId);
void insert(Content content);
void update(Content content);
void delete(Long id);
List<Content> search(String q, int offset, int size);
long countSearch(String q);
```

### 5.5 ContentCategoryMapper

```java
void insert(Long contentId, Long categoryId);
void deleteByContentId(Long contentId);
List<Long> findCategoryIdsByContentId(Long contentId);
List<ContentCategoryLink> findLinksByContentIds(List<Long> contentIds);
long countContentsByCategoryId(Long categoryId);
```

### 5.6 ContentTagMapper

```java
void insert(Long contentId, Long tagId);
void deleteByContentId(Long contentId);
List<Long> findTagIdsByContentId(Long contentId);
List<ContentTagLink> findLinksByContentIds(List<Long> contentIds);
long countContentsByTagId(Long tagId);
```

### 5.7 ImageMapper

```java
void insert(Image image);
Image findById(Long id);
```

### 5.8 SiteOptionMapper

```java
List<SiteOption> findAll();
SiteOption findByKey(String optionKey);
void upsert(String key, String value);
```

---

## 6. SQL 行为

### 6.1 排序

Mapper XML 只有两个排序分支：

```xml
<when test="sort == 'created_at_desc'">
    ORDER BY c.created_at DESC
</when>
<otherwise>
    ORDER BY c.published_at DESC
</otherwise>
```

因此除了 `created_at_desc`，任何输入都按 `published_at DESC`。

### 6.2 已发布条件

公开列表固定附加：

```sql
WHERE c.status = 'PUBLISHED'
```

公开详情固定附加：

```sql
WHERE c.slug = #{slug} AND c.status = 'PUBLISHED'
```

### 6.3 分类和标签筛选

使用解析后 ID 集合的 `EXISTS`：

```sql
EXISTS (
  SELECT 1
  FROM content_category cc
  WHERE cc.content_id = c.id
    AND cc.category_id IN (...)
)
```

`CategoryHierarchyResolver` 在默认情况下返回单元素集合，保持精确匹配；启用
`includeDescendants` 后按分类树展开全部后代，使用 `Set` 语义和 `EXISTS` 避免重复内容。
命中范围内循环或超过 32 层返回 409。排序在原有时间字段后追加 `id DESC`。

### 6.4 搜索

```sql
WHERE c.status = 'PUBLISHED'
  AND (
    c.title LIKE CONCAT('%', #{q}, '%')
    OR c.summary LIKE CONCAT('%', #{q}, '%')
  )
```

不查询文件系统，也不查询 Markdown 正文。

### 6.5 详情前后文章

`findPreviousPublished` 只返回 `PUBLISHED` 且发布时间更早的记录：

```sql
published_at < #{publishedAt}
OR (published_at = #{publishedAt} AND id < #{id})
ORDER BY published_at DESC, id DESC
LIMIT 1
```

`findNextPublished` 使用相反方向和排序，同时间以大 ID 为更晚。

### 6.6 站点配置更新

```sql
INSERT INTO site_options (option_key, option_value)
VALUES (#{key}, #{value})
ON DUPLICATE KEY UPDATE option_value = VALUES(option_value)
```

可以插入数据库中尚不存在的配置 key。

---

## 7. 关联删除

内容更新和删除仍按 `contentId` 清理关联：

```java
deleteByContentId(contentId)
```

分类和标签删除不删除关联，而是分别通过：

```java
countContentsByCategoryId(categoryId)
countContentsByTagId(tagId)
```

有关联时返回 409。`CategoryMapper.countChildren(parentId)` 阻止删除仍有子分类的父分类。

### 7.1 批量关联查询

`findLinksByContentIds(contentIds)` 一次查询多篇文章的分类/标签映射。
`ContentVOMapper` 每个列表请求固定执行：

```text
1 次 content_category 批量查询
1 次 content_tag 批量查询
1 次 categories findByIds
1 次 tags findByIds
```

查询次数不随文章数量增加；`ObjectMapper` 也由共享组件单例注入。

### 7.2 数据库约束

`schema.sql` 已加入：

- `content_category.category_id` 索引和双向外键。
- `content_tag.tag_id` 索引和双向外键。
- `contents.published_at` 索引。
- `categories.parent_id` 自引用外键。

其中内容外键使用 `ON DELETE CASCADE`，分类和标签外键使用 `ON DELETE RESTRICT`。
已有数据库使用 `docs/design/migrations/20260911_integrity_security.sql` 先清理孤儿行、再补列、
索引和外键。`users.token_version` 也由该脚本兼容添加。

---

## 8. 测试现状

单元测试覆盖路径、文件事务、JWT、限流、VO 批量组装和上传签名；Controller 边界测试仍 Mock Service。
GitHub Actions 的 MySQL 8.4 job 另行启动真实后端并验证 Mapper SQL：

- 没有 Entity 与 Schema 的自动一致性测试。
- 没有 SQL 注入和分页边界测试。

2026-09-13 起 CI 会从空库执行 Schema、种子数据、兼容迁移和 27/27 接口冒烟，
覆盖 Mapper 查询、分类/标签关联与详情前后文章；2026-09-11 隔离 MySQL 5.7 副本记录继续保留。

构建和测试命令见 [codebase-memory.md](../project/codebase-memory.md)。
