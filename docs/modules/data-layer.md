# Data 层实现

> 基线日期: 2026-09-10
> 路径: `model/`、`mapper/`、`resources/mapper/`

---

## 1. 模型数量

| 类别 | 数量 |
|---|---:|
| Entity | 6 |
| DTO | 8 |
| VO | 6 |
| Mapper 接口 | 8 |
| Mapper XML | 8 |

---

## 2. Entity

### 2.1 User

```java
Long id;
String username;
String passwordHash;
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
| `ContentSaveRequest` | `title`、`slug`、`body`、`summary`、`type`、`status`、`categoryIds`、`tagIds`、`metadata` | `title`、`slug`、`type` 必填 |
| `ContentQuery` | `page`、`size`、`type`、`categoryId`、`tagId`、`status`、`sort`、`q` | 无约束注解 |
| `CategorySaveRequest` | `name`、`slug`、`parentId`、`type`、`sortOrder` | `name`、`slug` 必填 |
| `TagSaveRequest` | `name`、`slug` | 两者必填 |
| `OptionSaveRequest` | `value` | 必填 |
| `PageResult<T>` | `items`、`page`、`size`、`total` | 分页包装 |

`ContentQuery.getOffset()` 当前直接计算：

```java
(page - 1) * size
```

没有防止 `page <= 0` 或 `size <= 0`。

---

## 4. VO

| VO | 字段 |
|---|---|
| `ContentListVO` | `id`、`title`、`slug`、`summary`、`type`、`categories`、`tags`、`metadata`、`publishedAt` |
| `ContentDetailVO` | 继承列表 VO，增加 `body` |
| `CategoryTreeVO` | `id`、`name`、`slug`、`type`、`children` |
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
List<Content> findPublished(ContentQuery query);
long countPublished(ContentQuery query);
Content findBySlug(String slug);
List<Content> findAll(ContentQuery query);
long countAll(ContentQuery query);
Content findById(Long id);
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
```

### 5.6 ContentTagMapper

```java
void insert(Long contentId, Long tagId);
void deleteByContentId(Long contentId);
List<Long> findTagIdsByContentId(Long contentId);
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

使用精确 `EXISTS`：

```sql
EXISTS (
  SELECT 1
  FROM content_category cc
  WHERE cc.content_id = c.id
    AND cc.category_id = #{categoryId}
)
```

不会自动展开子分类。

### 6.4 搜索

```sql
WHERE c.status = 'PUBLISHED'
  AND (
    c.title LIKE CONCAT('%', #{q}, '%')
    OR c.summary LIKE CONCAT('%', #{q}, '%')
  )
```

不查询文件系统，也不查询 Markdown 正文。

### 6.5 站点配置更新

```sql
INSERT INTO site_options (option_key, option_value)
VALUES (#{key}, #{value})
ON DUPLICATE KEY UPDATE option_value = VALUES(option_value)
```

可以插入数据库中尚不存在的配置 key。

---

## 7. 关联删除

当前 Mapper 只提供：

```java
deleteByContentId(contentId)
```

没有提供 `deleteByCategoryId` 或 `deleteByTagId`。管理端文章更新和文章删除会调用它们，但分类和标签删除保护错误地复用了按内容 ID 查询的方法。

### 7.1 已知缺陷

`CategoryManageServiceImpl.delete(id)`：

```java
contentCategoryMapper.findCategoryIdsByContentId(id)
```

这里传入分类 ID，语义应为查询内容 ID，结果不能证明该分类是否被内容引用。

`TagManageServiceImpl.delete(id)`：

```java
contentTagMapper.findTagIdsByContentId(id)
```

存在同样问题。

### 7.2 数据库约束

`schema.sql` 没有定义外键。删除顺序完全依赖 Service，缺少数据库级完整性保护。

---

## 8. 测试现状

当前边界测试主要 Mock Service，没有使用真实 Mapper 或 MySQL：

- 没有 Entity 与 Schema 的自动一致性测试。
- 没有 Mapper XML 集成测试。
- 没有多对多关联事务测试。
- 没有 SQL 注入和分页边界测试。

构建和测试命令见 [codebase-memory.md](../project/codebase-memory.md)。
